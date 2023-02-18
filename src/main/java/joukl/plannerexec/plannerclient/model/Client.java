package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class Client {
    private Authorization authorization = new Authorization();
    private List<Task> tasks = new ArrayList<>();
    //TODO nasetování queue
    private List<String> queue = Arrays.asList("test1", "test2", "testQ3");
    //new LinkedList<>();
    private String schedulerAddress = "127.0.0.1";
    //TODO z configu?
    private String USER_AGENT = "WINDOWS";

    private long availableResources;

    private Socket socket;

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

    public void startPooling() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Client client = Client.getClient();
        socket = new Socket(this.schedulerAddress, 6660);
        //out = new PrintWriter(socket.getOutputStream(), true);
        // in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

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


        //fake wrapper for output stream - as cipher stream won't actually send when flushed.
        //NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(socket.getOutputStream());
        //* CipherOutputStream AESoutStream = new CipherOutputStream(socket.getOutputStream(), aesEncrypting);

        //* Cipher aesDecrypting = Cipher.getInstance("AES");
        //* aesDecrypting.init(Cipher.DECRYPT_MODE, secKey);

        //  CipherInputStream AESInputStream = new CipherInputStream(socket.getInputStream(), aesDecrypting);

        //0x1C
        //CipherOutputStream outStream = new CipherOutputStream(socket.getOutputStream(), cipherOut);
        socket.setTcpNoDelay(true);
        try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            InputStream in = socket.getInputStream();
            // Send key for symetric cryptography
            out.write(encryptedKey);

            //TODO metoda, první setup
            if(client.getId() == null) {
                byte[] messageToSend = (String.format("NEW;%s;%s", client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8);

                sendEncryptedString(aesEncrypting, out, messageToSend);
                sendEncryptedList(aesEncrypting, out, queue);
                //read id
                String id = readEncryptedString(aesDecrypting, in);

                client.setId(id);
            }



            //String message = readEncryptedString(aesDecrypting, in);

            // end of symetric key exchange
            //declare fake closing output stream

            //send client info
            //*  AESoutStream.write((String.format("NEW;%s;%s", client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8));
            //* AESoutStream.flush();

            //stop :)

            //   byte[] bytes = readBytesUntilStop(AESInputStream);
            //   String message = new String(bytes, StandardCharsets.UTF_8);
            //   System.out.println("response:"+message);

            //     AESoutStream.write("dalsi".getBytes(StandardCharsets.UTF_8));
            //    AESoutStream.flush();
        }
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
        for (int i = 0; i < toEncrypt.size(); i++) {
            //encode each item
            String encodedItem = Base64.getEncoder().encodeToString(toEncrypt.get(i).getBytes(StandardCharsets.UTF_8));
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
}
