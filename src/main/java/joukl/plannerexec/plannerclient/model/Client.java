package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

import static java.io.File.separator;
import static joukl.plannerexec.plannerclient.model.Persistence.zipFile;

public class Client {
    private Authorization authorization = new Authorization();

    private ScheduledExecutorService executor;

    private static final int RETRY_COUNT = 10;

    //works with one thread scheduling only
    private boolean connectMessageDisplayed = false;

    public static final String PATH_TO_TASK_STORAGE = "storage" + separator + "tasks" + separator;
    public static final String PATH_TO_TASK_RESULTS_STORAGE = "storage" + separator + "results" + separator;

    List<Thread> runningThreadsList = Collections.synchronizedList(new LinkedList<>());
    private List<Task> tasks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> queues = new ArrayList<>();
    //new LinkedList<>();
    private String schedulerAddress = "127.0.0.1";
    private String userAgent = "WINDOWS";

    private int initialAvailableResources;
    private volatile AtomicInteger availableResources = new AtomicInteger(initialAvailableResources);

    private volatile String id;

    private final char STOP_SYMBOL = 31;
    private final char ARR_STOP_SYMBOL = 30;
    private int port;


    private static final Client CLIENT = new Client();

    private Client() {
        Configuration config = Persistence.readApplicationConfiguration();
        if (config != null) {
            port = config.getPort();
            userAgent = config.getAgent();
            queues.addAll(config.getSubscribedQueues());
            initialAvailableResources = config.getAvailableResources();
            availableResources = new AtomicInteger(initialAvailableResources);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean loadClientKeys() {
        boolean loaded = false;
        try {
            loaded = authorization.loadPrivateKeyFromRoot();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            throw new RuntimeException(e);
        }
        return loaded;
    }

    public long getInitialAvailableResources() {
        return initialAvailableResources;
    }

    public void setInitialAvailableResources(int initialAvailableResources) {
        this.initialAvailableResources = initialAvailableResources;
    }

    public boolean loadServerPublicKey() {
        boolean loaded = false;
        try {
            loaded = authorization.loadServerKeyFromRoot();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            throw new RuntimeException(e);
        }
        return loaded;
    }

    public boolean generateClientKeys() throws NoSuchAlgorithmException {
        return authorization.generateClientKeys();
    }

    public void pool() {

    }

    public static Client getClient() {
        return CLIENT;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public AtomicInteger getAvailableResources() {
        return availableResources;
    }

    public void setAvailableResources(AtomicInteger availableResources) {
        this.availableResources = availableResources;
    }

    public String getSchedulerAddress() {
        return schedulerAddress;
    }

    public void setSchedulerAddress(String schedulerAddress) {
        this.schedulerAddress = schedulerAddress;
    }

    public void startPooling() throws IOException {
        executor = Executors.newScheduledThreadPool(4);
        //periodically create new pooling threads
        executor.scheduleAtFixedRate(() -> {
            Thread thread = new Thread(() -> {
                poolForTaskAndExecuteIt();
                runningThreadsList.remove(Thread.currentThread());
            });

            runningThreadsList.add(thread);
            thread.start();

        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void poolForTaskAndExecuteIt() {
        Task recievedTask = null;
        Socket socket = null;
        try {
            socket = new Socket(this.schedulerAddress, port);
            socket.setSoTimeout(2000); //2 s
            socket.setTcpNoDelay(true);
        } catch (IOException e) {
            if (!connectMessageDisplayed) {
                System.out.println("Failed to connect... You will be informed when connection will be established...");
                connectMessageDisplayed = true;
            }
            return;
        }
        //we got through initial connection, reset error
        if (connectMessageDisplayed) {
            System.out.println("Connection successful");
            connectMessageDisplayed = false;
        }

        SecretKey secKey = null;
        Cipher decrypting = null;
        Cipher aesDecrypting = null;
        Cipher encrypting = null;
        Cipher aesEncrypting = null;
        byte[] encryptedKey = null;
        try {
            secKey = generateAesKey();
            encryptedKey = encryptAesKey(secKey);

            //decrypting
            decrypting = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            decrypting.init(Cipher.DECRYPT_MODE, authorization.getClientPrivateKey());
            //symmetric
            aesDecrypting = Cipher.getInstance("AES");
            aesDecrypting.init(Cipher.DECRYPT_MODE, secKey);

            //encrypting
            encrypting = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encrypting.init(Cipher.ENCRYPT_MODE, authorization.getServerPublicKey());
            //symmetric
            aesEncrypting = Cipher.getInstance("AES");
            aesEncrypting.init(Cipher.ENCRYPT_MODE, secKey);
        } catch (Exception ignored) {
            //can't happen...
        }

        try {

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                InputStream in = socket.getInputStream();
                if (!validateServer(socket, decrypting, encrypting, out, in)) {
                    return;
                }
                String response;

                // Send key for symmetric cryptography
                out.write(encryptedKey);

                if (this.id == null) {
                    boolean registered = sendRequestToRegister(aesDecrypting, aesEncrypting, out, in);
                    if (!registered) {
                        socket.close();
                        return;
                    }
                } else {
                    introduceExistingClient(aesDecrypting, aesEncrypting, out, in, IntroductionMode.GREETING);
                }
                //determine if we will get task
                response = readEncryptedString(aesDecrypting, in);
                if (response.equals("NO_TASK")) {
                    //we got no task, we will later return to pooling
                } else {
                    recievedTask = receiveTaskWithBasicInfo(socket, aesDecrypting, aesEncrypting, out, in, response);
                    if (recievedTask == null) return;
                    tasks.add(recievedTask);
                    recievedTask.setStatus(TaskStatus.UPLOADED);
                }
            }
            socket.close();
            //if we have task, unzip it and run it
            if (recievedTask != null) {
                //clean up recieved directory
                Persistence.cleanUpReceived(recievedTask.getId(), false);
                Persistence.unzip(recievedTask);
                recievedTask.setPathToSource(Client.PATH_TO_TASK_STORAGE + recievedTask.getId());
                recievedTask = Persistence.mergeTaskWithConfiguration(recievedTask, new File(recievedTask.getPathToSource() + separator + "taskConfig.json"));
                boolean isValid = Task.validateCorrectParametrization(recievedTask);
                if (!isValid) {
                    throw new Exception("Task parametrization values are not valid");
                }
                //clean up results location
                Persistence.cleanUpRes(recievedTask);
                recievedTask.setStatus(TaskStatus.RUNNING);
                int res = recievedTask.run();

                if (res != 0) {
                    //probably failed, but job completed if no error occurred - meaning results are available
                    recievedTask.setStatus(TaskStatus.WARNING);
                } else {
                    //finished on client
                    recievedTask.setStatus(TaskStatus.ZIPPING);
                }
                //System.out.println("Task with id: " + recievedTask.getId() + " and name " + recievedTask.getName() + " ended with result: " + res + "\nCreating zip of file...");
                //create zip of task results
                zipResults(recievedTask);

                //transfer task opening socket
                recievedTask.setStatus(TaskStatus.IN_TRANSFER);
                boolean canCleanUp = transferTaskResults(decrypting, aesDecrypting, encrypting, aesEncrypting, encryptedKey, recievedTask);
                if (canCleanUp) {
                    try {
                        //System.out.println("cleaning up task " + recievedTask.getId());
                        Persistence.cleanUp(recievedTask);
                    } catch (Exception e) {
                        System.out.println("Failed to clean up task " + recievedTask.getId());
                    }
                }
            }
        } catch (Exception e) {
            //add task as failed
            if (recievedTask != null) {
                Task finalRecievedTask = recievedTask;
                finalRecievedTask.setStatus(TaskStatus.FAILED);
                availableResources.getAndUpdate((c) -> c + finalRecievedTask.getCost());
                //TODO inform about failure
                try {
                    informAboutFailure(decrypting, aesDecrypting, encrypting, aesEncrypting, encryptedKey, recievedTask);
                    System.out.println("Informed about task failure - id: " + recievedTask.getId());
                } catch (IOException | IllegalBlockSizeException | BadPaddingException ex) {
                    System.out.println("Failed to inform about failure of task " + recievedTask.getId());
                }
            }
        }
    }

    private Task receiveTaskWithBasicInfo(Socket socket, Cipher aesDecrypting, Cipher aesEncrypting, DataOutputStream out, InputStream in, String response) throws IllegalBlockSizeException, BadPaddingException, IOException {
        // we will get task - message it is id
        String[] responses = response.split(";");
        String taskId = responses[0];
        int cost = Integer.parseInt(responses[1]);
        if (checkIfCostIsHigherAndSetResources(cost)) {
            sendEncryptedString(aesEncrypting, out, "COST_TOO_HIGH".getBytes(StandardCharsets.UTF_8));
            socket.close();
            return null;
        }
        sendEncryptedString(aesEncrypting, out, availableResources.toString().getBytes(StandardCharsets.UTF_8));
        Task recievedTask = receiveTaskFiles(aesDecrypting, in, taskId, cost);
        return recievedTask;
    }

    private synchronized boolean checkIfCostIsHigherAndSetResources(int cost) {
        if (cost > availableResources.get()) {
            return true;
        }
        availableResources.getAndUpdate((curr) -> curr - cost);
        return false;
    }

    private static void zipResults(Task recievedTask) throws IOException {
        FileOutputStream fos = new FileOutputStream(PATH_TO_TASK_RESULTS_STORAGE + recievedTask.getName() + separator + recievedTask.getId() + ".zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        File fileToZip = new File(PATH_TO_TASK_RESULTS_STORAGE + recievedTask.getName() + separator + recievedTask.getId());
        zipFile(fileToZip, recievedTask.getId(), zipOut);
        zipOut.close();
        fos.close();
    }

    private boolean informAboutFailure(Cipher decrypting, Cipher aesDecrypting, Cipher encrypting, Cipher aesEncrypting, byte[] encryptedKey, Task task) throws IOException, IllegalBlockSizeException, BadPaddingException {
        Socket socket = null;
        int retryCount = 0;
        while (socket == null && retryCount < RETRY_COUNT) {
            try {
                socket = new Socket(this.schedulerAddress, port);
            } catch (Exception e) {
                // System.out.println("Failed to connect for result transfer, retrying");
            }
            retryCount++;
        }

        if (socket == null) {
            System.out.println("Transferring error message failed");
            //failed to deliver task -> so task failed
            availableResources.getAndUpdate((c) -> c + task.getCost());
            return false;
        }

        boolean canCleanUp = false;

        socket.setSoTimeout(2000); //2 s

        socket.setTcpNoDelay(true);
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            InputStream in = socket.getInputStream();

            if (!validateServer(socket, decrypting, encrypting, out, in)) {
                return false;
            }

            // Send key for symmetric cryptography
            out.write(encryptedKey);
            //send message about task failure
            boolean createdNewId = introduceExistingClient(aesDecrypting, aesEncrypting, out, in, IntroductionMode.EXCEPTION_REPORTING);
            if (createdNewId) {
                return canCleanUp;
            }
            //send id of failed task
            sendEncryptedString(aesEncrypting, out, task.getId().getBytes(StandardCharsets.UTF_8));
        }
        socket.close();
        return canCleanUp;
    }


    /**
     * @param decrypting
     * @param aesDecrypting
     * @param encrypting
     * @param aesEncrypting
     * @param encryptedKey
     * @param task
     * @return boolean if result can be cleaned up or not
     * @throws IOException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    private boolean transferTaskResults(Cipher decrypting, Cipher aesDecrypting, Cipher encrypting, Cipher aesEncrypting, byte[] encryptedKey, Task task) throws IOException, IllegalBlockSizeException, BadPaddingException {

        Socket socket = null;
        int retryCount = 0;
        while (socket == null && retryCount < RETRY_COUNT) {
            try {
                socket = new Socket(this.schedulerAddress, port);
            } catch (Exception e) {
                System.out.println("Failed to connect for result transfer, retrying");
            }
            retryCount++;
        }

        if (socket == null) {
            //failed to deliver task -> so task failed
            task.setStatus(TaskStatus.FAILED_TRANSFER);
            availableResources.getAndUpdate((c) -> c + task.getCost());
            return false;
        }
        socket.setSoTimeout(2000); //2 s

        boolean canCleanUp = false;

        socket.setTcpNoDelay(true);
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            InputStream in = socket.getInputStream();

            if (!validateServer(socket, decrypting, encrypting, out, in)) {
                return false;
            }

            // Send key for symmetric cryptography
            out.write(encryptedKey);

            boolean createdNewId = introduceExistingClient(aesDecrypting, aesEncrypting, out, in, IntroductionMode.RESULT_TRANSFER);
            //we got new id so task is no longer relevant
            if (createdNewId) {
                return canCleanUp;
            }
            //send task id and status - exception is handled in different scenario
            sendEncryptedString(aesEncrypting, out, String.format("%s;%s", task.getId(), task.getStatus().name()).getBytes(StandardCharsets.UTF_8));
            String response = readEncryptedString(aesDecrypting, in);

            if (response.equals("NOT_FOUND")) {
                //remove selected task
                tasks.remove(task);
                availableResources.getAndUpdate((c) -> c + task.getCost());
                canCleanUp = true;
            } else {
                //was found
                //send it

                String transferResponse = response;
                while (!(transferResponse.equals("FINISHED") || transferResponse.equals("FAILED_FINAL") || transferResponse.equals("NOT_FOUND"))) {
                    sendZipOfResults(out, aesEncrypting, PATH_TO_TASK_RESULTS_STORAGE + task.getName() + separator + task.getId() + ".zip");
                    transferResponse = readEncryptedString(aesDecrypting, in);
                }
                canCleanUp = !transferResponse.equals("FAILED_FINAL");
                if (transferResponse.equals("FINISHED")) {
                    task.setStatus(TaskStatus.FINISHED);
                } else {
                    task.setStatus(TaskStatus.FAILED);
                }

                availableResources.getAndUpdate((c) -> c + task.getCost());
            }
        }
        socket.close();
        return canCleanUp;
    }

    private static SecretKey generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128); // The AES key size in number of bits
        SecretKey secKey = generator.generateKey();
        return secKey;
    }

    private byte[] encryptAesKey(SecretKey secKey) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher keyCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        keyCipher.init(Cipher.PUBLIC_KEY, authorization.getServerPublicKey());
        byte[] encryptedKey = keyCipher.doFinal(secKey.getEncoded()/*Secret Key From Step 1*/);
        return encryptedKey;
    }

    private synchronized static boolean validateServer(Socket socket, Cipher decrypting, Cipher encrypting, DataOutputStream out, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        //first challenge, response - write random UUID encrypted by server public key, he will send it back + another challenge for us
        String uuId = UUID.randomUUID().toString();
        out.write(encrypting.doFinal(uuId.getBytes(StandardCharsets.UTF_8)));
        String response = new String(decrypting.doFinal(in.readNBytes(256)));
        //validate response
        if (!uuId.equals(response)) {
            socket.close();
            return false;
        }
        //get challenge and send response back
        byte[] challenge = decrypting.doFinal(in.readNBytes(256));
        out.write(encrypting.doFinal(challenge));
        return true;
    }

    private synchronized Task receiveTaskFiles(Cipher aesDecrypting, InputStream in, String taskId, int cost) throws IOException, IllegalBlockSizeException, BadPaddingException {

        try (FileOutputStream fos = new FileOutputStream(PATH_TO_TASK_STORAGE + taskId + ".zip")) {
            int receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
            while (receivedChunkSize > 0) {
                //decrypt and write
                fos.write(aesDecrypting.doFinal(in.readNBytes(receivedChunkSize)));
                receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
            }
        }
        return new Task(taskId, cost, PATH_TO_TASK_STORAGE + taskId + ".zip");
    }

    private synchronized boolean introduceExistingClient(Cipher aesDecrypting, Cipher aesEncrypting, DataOutputStream out, InputStream in, IntroductionMode mode) throws IllegalBlockSizeException, BadPaddingException, IOException {
        byte[] messageToSend;
        if (mode == IntroductionMode.GREETING) {
            messageToSend = (String.format("%s;%s;%s", this.id, this.availableResources, this.userAgent)).getBytes(StandardCharsets.UTF_8);
        } else if (mode == IntroductionMode.EXCEPTION_REPORTING) {
            messageToSend = (String.format("%s;%s;%s;EXCEPTION", this.id, this.availableResources, this.userAgent)).getBytes(StandardCharsets.UTF_8);
        } else {
            messageToSend = (String.format("%s;%s;%s;RESULT", this.id, this.availableResources, this.userAgent)).getBytes(StandardCharsets.UTF_8);
        }

        sendEncryptedString(aesEncrypting, out, messageToSend);
        //await validation or get new id - also remove all tasks
        String response = readEncryptedString(aesDecrypting, in);
        //if not ACK, server was reset - clear tasks and set new id
        if (!response.equals("ACK")) {
            System.out.println("re-registering");
            //send list of queues to finish new registration
            this.sendEncryptedList(aesEncrypting, out, queues);

            this.id = response;
            this.tasks.clear();
            this.availableResources.set(initialAvailableResources);
            List<Thread> toRemove = new LinkedList<>();
            /* FIXME bugs
            for (Thread thread : runningThreadsList
            ) {
                //since list is ordered, stop all threads before current thread - they are running for old id
                if (thread.equals(Thread.currentThread())) {
                    break;
                }
                thread.interrupt();
                toRemove.add(thread);
            }
             */
            runningThreadsList.removeAll(toRemove);
            //got new id
            return true;
        } else {
            //was authenticated
            return false;
        }
    }

    private synchronized boolean sendRequestToRegister(Cipher aesDecrypting, Cipher aesEncrypting, DataOutputStream out, InputStream in) throws IllegalBlockSizeException, BadPaddingException, IOException {
        //solves problem with concurrency
        if (this.id != null) {
            return false;
        }
        System.out.println("registering...");
        byte[] messageToSend = (String.format("%s;%s;NEW;", this.userAgent, this.availableResources)).getBytes(StandardCharsets.UTF_8);

        sendEncryptedString(aesEncrypting, out, messageToSend);
        sendEncryptedList(aesEncrypting, out, queues);
        //read id
        String id = readEncryptedString(aesDecrypting, in);

        this.id = id;
        return true;
    }

    private String readEncryptedString(Cipher aesDecrypting, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        byte[] bytes = readBytesUntilStop(in, STOP_SYMBOL);
        String message = decrypt(bytes, aesDecrypting);
        return message;
    }

    private void sendEncryptedString(Cipher aesEncrypting, DataOutputStream out, byte[] messageToSend) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encrypt(messageToSend, aesEncrypting);
        String withStop = encrypted + STOP_SYMBOL;

        out.write(withStop.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private byte[] readBytesUntilStop(InputStream cipherInputStream, char stopSymbol) throws IOException {
        List<Byte> bytes = new ArrayList<>();
        int byteAsInt = cipherInputStream.read();
        while (!(byteAsInt == -1 || (char) byteAsInt == stopSymbol)) {
            bytes.add((byte) byteAsInt);
            byteAsInt = cipherInputStream.read();
        }
        byte[] bytesArr = new byte[bytes.size()];
        for (int i = 0; i < bytesArr.length; i++) {
            bytesArr[i] = bytes.get(i);
        }

        return bytesArr;
    }

    public String decrypt(byte[] encrypted, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(plainText, StandardCharsets.UTF_8);
    }

    public String encrypt(byte[] decrypted, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] cipherText = cipher.doFinal(decrypted);
        return Base64.getEncoder().encodeToString(cipherText);
    }

    public String encryptList(List<String> toEncrypt, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        StringBuilder encodedArrWithDeli = new StringBuilder();
        for (String item : toEncrypt) {
            //encode each item
            String encodedItem = Base64.getEncoder().encodeToString(item.getBytes(StandardCharsets.UTF_8));
            encodedArrWithDeli.append(encodedItem).append(STOP_SYMBOL);
        }

        //encrypt whole array
        String enryptedArr = encrypt(encodedArrWithDeli.toString().getBytes(StandardCharsets.UTF_8), cipher);
        //add delimeter
        enryptedArr += (ARR_STOP_SYMBOL);
        return enryptedArr;
    }

    public void sendEncryptedList(Cipher cipher, DataOutputStream out, List<String> toEncrypt) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encryptList(toEncrypt, cipher);
        out.write(encrypted.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private List<String> readEncryptedList(Cipher aesDecrypting, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        //read array bytes
        byte[] arrBase64 = readBytesUntilStop(in, ARR_STOP_SYMBOL);

        //decrypt message, so we have itemInBase64+stop_symbol
        String arrMessage = decrypt(arrBase64, aesDecrypting);
        //split items using deli
        List<String> base64Items = List.of(arrMessage.split(String.valueOf(STOP_SYMBOL)));

        return base64Items.stream().map((encodedItem) -> new String(Base64.getDecoder().decode(encodedItem), StandardCharsets.UTF_8)).toList();
    }

    //stops pooling and clears all tasks
    public void stopPooling() {
        //TODO notice - tasks have to return to pooling!!! - so inform about disconnect?
        if (executor != null) {
            executor.shutdown();
        }
        tasks.clear();
        for (Thread thread : runningThreadsList) {
            thread.interrupt();
        }
        runningThreadsList.clear();
    }

    public void sendEncryptedMessage(Cipher aesEncrypting, DataOutputStream out, byte[] messageToSend) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encrypt(messageToSend, aesEncrypting);
        String withStop = encrypted + STOP_SYMBOL;

        out.write(withStop.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendZipOfResults(DataOutputStream out, Cipher aesEncrypting, String pathToResults) throws IOException, IllegalBlockSizeException, BadPaddingException {
        File zipFile = new File(pathToResults);
        long remainingLength = zipFile.length();
        int readLength = 2048;
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            while (remainingLength > 0) {

                if (readLength > remainingLength) {
                    readLength = (int) remainingLength;
                }
                //encrypt
                byte[] encrypted = aesEncrypting.doFinal(fis.readNBytes(readLength));

                long encryptedLength = encrypted.length;
                //send size
                sendEncryptedMessage(aesEncrypting, out, Long.toString(encryptedLength).getBytes(StandardCharsets.UTF_8));
                //send bytes
                out.write(encrypted);

                remainingLength -= readLength;
            }
            //send 0 to signalize we are done
            sendEncryptedMessage(aesEncrypting, out, Long.toString(0).getBytes(StandardCharsets.UTF_8));
        }
    }
}
