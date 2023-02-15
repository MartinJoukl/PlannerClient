package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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

        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, secKey);
        CipherOutputStream AESoutStream = new CipherOutputStream(socket.getOutputStream(), aesCipher);

        Cipher aesInputCipher = Cipher.getInstance("AES");
        aesInputCipher.init(Cipher.DECRYPT_MODE, secKey);

        CipherInputStream AESInStream = new CipherInputStream(socket.getInputStream(), aesInputCipher);

        //0x1C
        //NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(socket.getOutputStream());
        //CipherOutputStream outStream = new CipherOutputStream(socket.getOutputStream(), cipherOut);
        try (DataOutputStream dOut = new DataOutputStream(socket.getOutputStream())) {
            // Send key for symetric cryptography
            dOut.write(encryptedKey);
            dOut.flush();
           // dOut.write('\n');
            // dOut.close();
            //  dOut.writeChar('\n');

            byte[] test = AESInStream.readAllBytes();
            System.out.println(new String(test,StandardCharsets.UTF_8));

            AESoutStream.write((String.format("NEW;%s;%s;", client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8));
            AESoutStream.flush();
            System.out.println("succ");
            // socket.getInputStream().close();



            /*
            StringBuilder sb = new StringBuilder();
            for (String q : queue) {
                sb.append(q);
                sb.append((char) 0x1C);
            }


            String parsed = sb.toString();
            outStream.write(parsed.getBytes(StandardCharsets.UTF_8));
            outStream.flush();

             */
        }
    }
}
