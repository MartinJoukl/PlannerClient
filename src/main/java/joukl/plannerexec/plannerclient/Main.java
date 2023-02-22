package joukl.plannerexec.plannerclient;

import joukl.plannerexec.plannerclient.model.Client;
import joukl.plannerexec.plannerclient.model.KeyType;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import static joukl.plannerexec.plannerclient.model.Authorization.PATH_TO_KEYS;

public class Main {
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
                            "stip/setSchedulerIp - set scheduler ip\n" +
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
                case "stip":
                case "setSchedulerIp":
                    //TODO regex
                    System.out.println("enter new adress");
                    String address = sc.nextLine();
                    client.setSchedulerAddress(address);
                    break;
                case "cn":
                case "connect":
                    client.startPooling();
                    boolean stopPooling = false;
                    Scanner sc2 = new Scanner(System.in);
                    while (!stopPooling) {
                        System.out.println("Enter \"stop\" to stop pooling");
                        String line = sc2.nextLine();
                        stopPooling = line.equals("stop");
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
}