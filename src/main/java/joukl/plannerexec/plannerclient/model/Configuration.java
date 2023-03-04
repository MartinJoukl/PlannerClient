package joukl.plannerexec.plannerclient.model;

import java.util.List;

public class Configuration {

    public Configuration() {
    }

    private int port;
    private String host;
    private String agent;
    private List<String> subscribedQueues;
    private int availableResources;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public List<String> getSubscribedQueues() {
        return subscribedQueues;
    }

    public void setSubscribedQueues(List<String> subscribedQueues) {
        this.subscribedQueues = subscribedQueues;
    }

    public int getAvailableResources() {
        return availableResources;
    }

    public void setAvailableResources(int availableResources) {
        this.availableResources = availableResources;
    }
}
