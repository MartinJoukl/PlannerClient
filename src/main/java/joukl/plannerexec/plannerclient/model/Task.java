package joukl.plannerexec.plannerclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class Task {
    private String id;
    private long cost;
    private String name;
    private String pathToExecutable;
    private List<String> pathToResults;
    private List<String> parameters;
    private int from;

    private int to;
    private boolean isRepeating;
    @JsonProperty("timeout")
    private long timeoutInMillis;
    private int priority;
    private String queue;
    private TaskStatus status;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public int getFrom() {
        return from;
    }

    public void setFrom(int from) {
        this.from = from;
    }

    public int getTo() {
        return to;
    }

    public void setTo(int to) {
        this.to = to;
    }

    public boolean isRepeating() {
        return isRepeating;
    }

    public void setRepeating(boolean repeating) {
        isRepeating = repeating;
    }

    public long getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public void setTimeoutInMillis(long timeoutInMillis) {
        this.timeoutInMillis = timeoutInMillis;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getPathToExecutable() {
        return pathToExecutable;
    }

    public void setPathToExecutable(String pathToExecutable) {
        this.pathToExecutable = pathToExecutable;
    }

    public List<String> getPathToResults() {
        return pathToResults;
    }

    public void setPathToResults(List<String> pathToResults) {
        this.pathToResults = pathToResults;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //TODO odstranit, použít implicitní?
    public Task(@JsonProperty("cost") long cost, @JsonProperty("name") String name,
                @JsonProperty("pathToExecutable") String pathToExecutable, @JsonProperty("pathToResults") List<String> pathToResults,
                @JsonProperty("timeout") long timeoutInMillis, @JsonProperty("priority") int priority, @JsonProperty("queue") String queueName) {
        this.cost = cost;
        this.name = name;
        this.pathToExecutable = pathToExecutable;
        this.pathToResults = pathToResults;
        this.timeoutInMillis = timeoutInMillis;
        this.priority = priority;
        this.queue = queueName;

        this.id = UUID.randomUUID().toString();

        this.status = TaskStatus.SCHEDULED;
    }
}
