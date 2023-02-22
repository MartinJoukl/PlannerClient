package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.io.File.separator;

public class Client {
    private Authorization authorization = new Authorization();

    ScheduledExecutorService executor;

    //works with one thread scheduling only
    private boolean connectMessageDisplayed = false;

    public static final String PATH_TO_TASK_STORAGE = "storage" + separator + "tasks" + separator;
    public static final String PATH_TO_TASK_RESULTS_STORAGE = "storage" + separator + "results" + separator;

    List<Thread> runningThreadsList = Collections.synchronizedList(new LinkedList<>());
    private List<Task> tasks = new ArrayList<>();
    //TODO nasetování queue
    private final List<String> queue = new ArrayList<>();
    //new LinkedList<>();
    private String schedulerAddress = "127.0.0.1";
    //TODO z configu?
    private String USER_AGENT = "WINDOWS";

    //TODO z configu, pro test 11
    private long initialAvailableResources = 11;
    private long availableResources = initialAvailableResources;

    private String id;

    private final char STOP_SYMBOL = 31;
    private final char ARR_STOP_SYMBOL = 30;


    private static final Client CLIENT = new Client();

    private Client() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public void setInitialAvailableResources(long initialAvailableResources) {
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

    public long getAvailableResources() {
        return availableResources;
    }

    public void setAvailableResources(long availableResources) {
        this.availableResources = availableResources;
    }

    public String getSchedulerAddress() {
        return schedulerAddress;
    }

    public void setSchedulerAddress(String schedulerAddress) {
        this.schedulerAddress = schedulerAddress;
    }

    public void startPooling() throws IOException {
        Client client = Client.getClient();

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        //periodically create new pooling threads
        executor.scheduleAtFixedRate(() -> {
            Thread thread = new Thread(() -> {
                poolForTaskAndExecuteIt(client);
                runningThreadsList.remove(Thread.currentThread());
            });

            runningThreadsList.add(thread);
            thread.start();

        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void poolForTaskAndExecuteIt(Client client) {
        Task recievedTask = null;
        Socket socket = null;
        try {
            socket = new Socket(this.schedulerAddress, 6660);
        } catch (IOException e) {
            if (!connectMessageDisplayed) {
                System.out.println("Failed to connect (You will be informed when we establish connection.)");
                connectMessageDisplayed = true;
            }
            return;
        }
        //we got througth initial connection, reset error
        if (connectMessageDisplayed) {
            System.out.println("Connection successful");

            connectMessageDisplayed = false;
        }

        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128); // The AES key size in number of bits
            SecretKey secKey = generator.generateKey();

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.PUBLIC_KEY, authorization.getServerPublicKey());
            byte[] encryptedKey = cipher.doFinal(secKey.getEncoded()/*Secret Key From Step 1*/);
            //decrypting
            Cipher aesDecrypting = Cipher.getInstance("AES");
            aesDecrypting.init(Cipher.DECRYPT_MODE, secKey);
            //encrypting
            Cipher aesEncrypting = Cipher.getInstance("AES");
            aesEncrypting.init(Cipher.ENCRYPT_MODE, secKey);

            socket.setTcpNoDelay(true);
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                InputStream in = socket.getInputStream();
                // Send key for symetric cryptography
                out.write(encryptedKey);

                if (client.getId() == null) {
                    sendRequestToRegister(client, aesDecrypting, aesEncrypting, out, in);
                } else {
                    introduceById(client, aesDecrypting, aesEncrypting, out, in);
                }
                //determine if we will get task
                String response = readEncryptedString(aesDecrypting, in);
                if (response.equals("NO_TASK")) {
                    //we got no task, return to pooling
                } else {
                    // we will get task - message it is id
                    String taskId = response;

                    try (FileOutputStream fos = new FileOutputStream(PATH_TO_TASK_STORAGE + taskId + ".zip")) {
                        int receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
                        while (receivedChunkSize > 0) {
                            //decrypt and write
                            fos.write(aesDecrypting.doFinal(in.readNBytes(receivedChunkSize)));
                            receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
                        }
                    }
                    recievedTask = new Task(taskId, PATH_TO_TASK_STORAGE + taskId + ".zip");
                }
            }
            socket.close();
            //if we have task, unzip it and run it
            if (recievedTask != null) {
                System.out.println("after close");
                Persistence.unzip(recievedTask);
                recievedTask.setPathToSource(Client.PATH_TO_TASK_STORAGE + recievedTask.getId());
                recievedTask = Persistence.mergeTaskWithConfiguration(recievedTask, new File(recievedTask.getPathToSource() + separator + "taskConfig.json"));
                int res = recievedTask.run();
                System.out.println("Task ended with result: " + res);
            }
        } catch (Exception e) {
            if (recievedTask != null) {
                System.out.println("Something went wrong during task name: " + recievedTask.getName() + ", id: " + recievedTask.getId() + " processing " + e.getMessage());
            }
            //add task as failed
            if (recievedTask != null) {
                recievedTask.setStatus(TaskStatus.FAILED);
            }
        }
    }

    private void introduceById(Client client, Cipher aesDecrypting, Cipher aesEncrypting, DataOutputStream out, InputStream in) throws IllegalBlockSizeException, BadPaddingException, IOException {
        byte[] messageToSend = (String.format("%s;%s;%s", client.getId(), client.getAvailableResources(), client.USER_AGENT)).getBytes(StandardCharsets.UTF_8);

        sendEncryptedString(aesEncrypting, out, messageToSend);
        //await validation or get new id - also remove all tasks
        String response = readEncryptedString(aesDecrypting, in);
        //if not ACK, server was reset - clear tasks and set new id
        if (!response.equals("ACK")) {
            //send list of queues to finish new registration
            client.sendEncryptedList(aesEncrypting, out, queue);

            client.setId(response);
            //TODO stop tasks
            client.getTasks().clear();
            client.setAvailableResources(client.getInitialAvailableResources());
            List<Thread> toRemove = new LinkedList<>();
            for (Thread thread : runningThreadsList
            ) {
                //since list is ordered, stop all threads before current thread - they are running for old id
                if (thread.equals(Thread.currentThread())) {
                    break;
                }
                thread.interrupt();
                toRemove.add(thread);
            }
            runningThreadsList.removeAll(toRemove);
        }
    }

    private void sendRequestToRegister(Client client, Cipher aesDecrypting, Cipher aesEncrypting, DataOutputStream out, InputStream in) throws IllegalBlockSizeException, BadPaddingException, IOException {
        byte[] messageToSend = (String.format("NEW;%s;%s", client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8);

        sendEncryptedString(aesEncrypting, out, messageToSend);
        sendEncryptedList(aesEncrypting, out, queue);
        //read id
        String id = readEncryptedString(aesDecrypting, in);

        client.setId(id);
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

        return base64Items.stream()
                .map((encodedItem) -> new String(Base64.getDecoder().decode(encodedItem), StandardCharsets.UTF_8))
                .toList();
    }

    //stops pooling and clears all tasks
    public void stopPooling() {
        //TODO notice - tasks have to return to pooling!!!
        if (executor != null) {
            executor.shutdown();
        }
        tasks.clear();
        for (Thread thread : runningThreadsList
        ) {
            thread.interrupt();
        }
        runningThreadsList.clear();
    }
}
