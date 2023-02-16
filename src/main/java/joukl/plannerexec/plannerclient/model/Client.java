package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
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

    private PrintWriter out;
    private BufferedReader in;
    private long availableResources;

    private Socket socket;

    private String id;

    private final char STOP_SYMBOL = (char) 1f;


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

            for (int i = 0; i < 1000; i--) {

                byte[] messageToSend = (String.format("NEW;%s;%s"+i, client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8);

                sendEncryptedMessage(aesEncrypting, out, messageToSend);
                String message = readEncryptedString(aesDecrypting, in);
                System.out.println(message);
            }
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
        byte[] bytes = readBytesUntilStop(in);
        String message = decrypt(bytes, aesDecrypting);
        return message;
    }

    private void sendEncryptedMessage(Cipher aesEncrypting, DataOutputStream out, byte[] messageToSend) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encrypt(messageToSend, aesEncrypting);
        String withStop = encrypted+STOP_SYMBOL;

        out.write(withStop.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private byte[] readBytesUntilStop(InputStream cipherInputStream) throws IOException {
        List<Byte> bytes = new ArrayList<>();
        int byteAsInt = cipherInputStream.read();
        while (!(byteAsInt == -1 || (char) byteAsInt == STOP_SYMBOL)) {
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
        return new String(plainText);
    }

    public String encrypt(byte[] decrypted, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] cipherText = cipher.doFinal(decrypted);
        return Base64.getEncoder().encodeToString(cipherText);
    }
}
