package joukl.plannerexec.plannerclient.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static joukl.plannerexec.plannerclient.model.KeyType.*;

public class Authorization {
    public static final String PATH_TO_KEYS = "storage/keys";
    private PublicKey serverPublicKey;
    private PublicKey clientPublicKey;

    private PrivateKey clientPrivateKey;

    public boolean loadPrivateKeyFromRoot() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(PATH_TO_KEYS + "/" + CLIENT_PRIVATE.getKeyName() + ".key"));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
            clientPrivateKey = keyFactory.generatePrivate(keySpec);

            RSAPrivateCrtKey privk = (RSAPrivateCrtKey) clientPrivateKey;
            RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());

            clientPublicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (IOException ignored) {
            return false;
        }
        //refresh public key
        saveKeyToStorage(CLIENT_PUBLIC);
        return true;
    }
    public boolean loadServerKeyFromRoot() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(PATH_TO_KEYS + "/" + SERVER_PUBLIC.getKeyName() + ".key"));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
            serverPublicKey = keyFactory.generatePublic(keySpec);
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }

    public boolean changeServerKeys(File file) throws NoSuchAlgorithmException, IOException {
        byte[] key = Files.readAllBytes(Paths.get(file.getPath()));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
        try {
            clientPrivateKey = keyFactory.generatePrivate(keySpec);

            RSAPrivateCrtKey privk = (RSAPrivateCrtKey) clientPrivateKey;
            RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());

            clientPublicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        saveKeyToStorage(CLIENT_PRIVATE);
        saveKeyToStorage(CLIENT_PRIVATE);

        System.out.println("Private key format: " + clientPrivateKey.getFormat());

        System.out.println("Public key format: " + clientPublicKey.getFormat());
        return true;
    }

    //Won't use
    public boolean changeServerKey(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(file.getPath()));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
            serverPublicKey = keyFactory.generatePublic(keySpec);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        saveKeyToStorage(SERVER_PUBLIC);

        System.out.println("Client public key format: " + serverPublicKey.getFormat());
        return true;
    }

    public boolean generateClientKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keys = kpg.generateKeyPair();
        clientPrivateKey = keys.getPrivate();
        clientPublicKey = keys.getPublic();
        saveKeyToStorage(CLIENT_PRIVATE);
        saveKeyToStorage(CLIENT_PUBLIC);


       // System.out.println("Private key format: " + clientPrivateKey.getFormat());
// prints "Private key format: PKCS#8" on my machine

       // System.out.println("Public key format: " + clientPublicKey.getFormat());
// prints "Public key format: X.509" on my machine
        return true;
    }

    public boolean loadServerPublicKey(String path) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        byte[] key = Files.readAllBytes(Paths.get(path));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
        try {
            serverPublicKey = keyFactory.generatePublic(keySpec);
            saveKeyToStorage(SERVER_PUBLIC);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        return true;
    }

    public void saveKeyToStorage(KeyType keyType) {
        switch (keyType) {
            case SERVER_PUBLIC -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + SERVER_PUBLIC.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, serverPublicKey.getEncoded());
            }
            case CLIENT_PRIVATE -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + CLIENT_PRIVATE.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, clientPrivateKey.getEncoded());
            }
            case CLIENT_PUBLIC -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + CLIENT_PUBLIC.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, clientPublicKey.getEncoded());
            }
        }
    }

    private PublicKey generatePublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        PKCS8EncodedKeySpec keyspec = keyFactory.getKeySpec(clientPrivateKey, PKCS8EncodedKeySpec.class);
        return keyFactory.generatePublic(keyspec);
    }

    public PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (clientPublicKey == null) {
            return generatePublicKey();
        }
        return clientPublicKey;
    }

    public PrivateKey getClientPrivateKey() {
        return clientPrivateKey;
    }

    public void setClientPrivateKey(PrivateKey clientPrivateKey) {
        this.clientPrivateKey = clientPrivateKey;
    }

    public void setServerPublicKey(PublicKey serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    public PublicKey getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(PublicKey clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

}
