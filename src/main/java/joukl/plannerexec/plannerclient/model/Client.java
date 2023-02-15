package joukl.plannerexec.plannerclient.model;

import javax.crypto.*;
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

    public void startPooling() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        Client client = Client.getClient();
        socket = new Socket(this.schedulerAddress, 6660);
        //out = new PrintWriter(socket.getOutputStream(), true);
        // in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


        Cipher cipherIn = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipherIn.init(Cipher.DECRYPT_MODE, authorization.getClientPrivateKey());

        CipherInputStream inputStream = new CipherInputStream(socket.getInputStream(), cipherIn);

        Cipher cipherOut = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipherOut.init(Cipher.ENCRYPT_MODE, authorization.getServerPublicKey());

        //0x1C
        NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(socket.getOutputStream());
        try (CipherOutputStream outStream = new CipherOutputStream(notClosingOutputStream, cipherOut)) {
            outStream.write((String.format("NEW;%s;%s;", client.USER_AGENT, client.getAvailableResources())).getBytes(StandardCharsets.UTF_8));
            outStream.flush();
            outStream.close();

            if (inputStream.read() == 0x06) {
                System.out.println("got ack!");
            }


            StringBuilder sb = new StringBuilder();
            for (String q : queue) {
                sb.append(q);
                sb.append((char) 0x1C);
            }


            String parsed = sb.toString();
            outStream.write(parsed.getBytes(StandardCharsets.UTF_8));
            outStream.flush();
        }
    }
}
