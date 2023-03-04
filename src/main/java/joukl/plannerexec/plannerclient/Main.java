package joukl.plannerexec.plannerclient;

import joukl.plannerexec.plannerclient.model.Client;
import joukl.plannerexec.plannerclient.model.KeyType;
import joukl.plannerexec.plannerclient.model.Task;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Scanner;

import static joukl.plannerexec.plannerclient.model.Authorization.PATH_TO_KEYS;

public class Main {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        System.out.println("""
                --------------------------------------------------------------------------------
                --------------------------------Executor client---------------------------------
                --------------------------------------------------------------------------------""");
        Client client = Client.getClient();
        //Try to load keys
        boolean loaded = client.loadClientKeys();
        if (loaded) {
            System.out.println("Client keys were successfully loaded");
        } else {
            System.out.println("Client keys were not found, you will need to generate new or import one!");
        }
        loaded = client.loadServerPublicKey();
        if (loaded) {
            System.out.println("Server public key was successfully loaded");
        } else {
            System.out.println("Server public key was not found, you will have to import one!");
        }

        boolean inputDone = false;
        System.out.print("Enter command (type \"h\" or \"help\" for help): ");
        Scanner sc = new Scanner(System.in);
        while (!inputDone) {
            String input = sc.nextLine();
            switch (input) {
                case "h":
                case "help":
                    System.out.println("COMMADS:\nh/help - displays list of commands \n" +
                            "gk/generateKey - generates client private and public key\n" +
                            "rsp/reloadServerPublic - reloads server public key from disc (from path " + PATH_TO_KEYS + "/" + KeyType.SERVER_PUBLIC.getKeyName() + ".key)\n" +
                            "rk/reloadKey - reloads keys from disc  (from path " + PATH_TO_KEYS + ")\n" +
                            "shst/setHost - set host ip or domain and port\n" +
                            "cn/connect - start pooling for tasks\n" +
                            "status - displays client info\n" +
                            "exit - exist program\n" +
                            "----------------------------------------"
                    );
                    break;
                case "gk":
                case "generateKey":
                    boolean success = client.generateClientKeys();
                    if (success) {
                        System.out.println("Key generation complete!");
                    } else {
                        System.out.println("Key generation failed");
                    }
                    break;
                case "rsp":
                case "reloadServerPublic":
                    success = client.loadClientKeys();
                    if (success) {
                        System.out.println("Keys reloaded sucesfully");
                    }
                    break;
                case "shst":
                case "setHost":
                    System.out.println("Enter new address or hostname");
                    String address = sc.nextLine();
                    client.setSchedulerAddress(address);
                    System.out.println("Enter port number");
                    int portNumber = sc.nextInt();
                    client.setPort(portNumber);
                    break;
                case "cn":
                case "connect":
                    client.startPooling();
                    boolean stopPooling = false;
                    Scanner sc2 = new Scanner(System.in);
                    System.out.println("Enter \"stop\" to stop pooling or \"ps\" to display running tasks");
                    while (!stopPooling) {
                        stopPooling = innerCommands(sc2);
                    }
                    client.stopPooling();
                    break;
                case "exit":
                    System.exit(0);
                    break;
            }
            System.out.print("Enter command (type \"h\" or \"help\" for help): ");
        }
    }

    private static boolean innerCommands(Scanner sc2) {
        String line = sc2.nextLine();
        if (line.equals("ps")) {
            StringBuilder sb = new StringBuilder();
            sb.append("---------------------------------ACCEPTED TASKS---------------------------------\n");
            sb.append(String.format("%-36s|%-15.15s|%-8s|%-5s|%-12.12s\n", "id", "name", "priority", "cost", "status"));

            List<Task> tasks = Client.getClient().getTasks();
            for (Task task : tasks) {
                sb.append(String.format("%-36s|%-15.15s|%-8s|%-5s|%-12.12s\n", task.getId(), task.getName(), task.getPriority(), task.getCost(), task.getStatus()));
            }
            System.out.print(sb);
        } else if (line.equals("stop")) {
            return true;
        } else {
            System.out.print("Enter \"stop\" to stop pooling or \"ps\" to display running tasks");
        }

        return false;
    }
}