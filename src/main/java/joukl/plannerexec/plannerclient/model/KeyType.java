package joukl.plannerexec.plannerclient.model;

public enum KeyType {
    SERVER_PUBLIC("serverPublic"),
    CLIENT_PRIVATE("clientPrivate"),
    CLIENT_PUBLIC("clientPublic");

    private String keyName;


    private KeyType(String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}
