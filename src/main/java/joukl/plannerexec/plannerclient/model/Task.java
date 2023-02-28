package joukl.plannerexec.plannerclient.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.io.File.separator;
import static joukl.plannerexec.plannerclient.model.Client.PATH_TO_TASK_RESULTS_STORAGE;
import static joukl.plannerexec.plannerclient.model.Client.PATH_TO_TASK_STORAGE;

public class Task {
    private Date startRunningTime;
    private String executePath;
    private String id;
    private int cost;
    private String name;
    private String commandToExecute;
    private List<String> pathToResults;
    private List<String> parameters;

    @JsonIgnore
    private String pathToZip;
    @JsonIgnore
    private String pathToSource;
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

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
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

    public String getCommandToExecute() {
        return commandToExecute;
    }

    public void setCommandToExecute(String commandToExecute) {
        this.commandToExecute = commandToExecute;
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

    public String getPathToZip() {
        return pathToZip;
    }

    public void setPathToZip(String pathToZip) {
        this.pathToZip = pathToZip;
    }

    public String getPathToSource() {
        return pathToSource;
    }

    public void setPathToSource(String pathToSource) {
        this.pathToSource = pathToSource;
    }

    public Task(String id, int cost, String pathToZip) {
        this.id = id;
        this.pathToZip = pathToZip;
        this.cost = cost;
    }

    public String getExecutePath() {
        return executePath;
    }

    public void setExecutePath(String executePath) {
        this.executePath = executePath;
    }

    //TODO odstranit, použít implicitní?
    public Task(@JsonProperty("cost") int cost, @JsonProperty("name") String name,
                @JsonProperty("commandToExecute") String commandToExecute, @JsonProperty("pathToResults") List<String> pathToResults,
                @JsonProperty("timeout") long timeoutInMillis, @JsonProperty("priority") int priority, @JsonProperty("queue") String queueName, @JsonProperty("executePath") String executePath) {
        this.cost = cost;
        this.name = name;
        this.commandToExecute = commandToExecute;
        this.pathToResults = pathToResults;
        this.timeoutInMillis = timeoutInMillis;
        this.priority = priority;
        this.queue = queueName;
        this.executePath = executePath;

        this.id = UUID.randomUUID().toString();

        this.status = TaskStatus.SCHEDULED;
    }

    public int run() throws IOException, InterruptedException, URISyntaxException {
        //create results location - also with name of command
        File resDir = new File(PATH_TO_TASK_RESULTS_STORAGE + name + separator + id);
        resDir.mkdirs();

        ArrayList<String> dtoIn = new ArrayList<>();

        File workingDir = new File(PATH_TO_TASK_STORAGE + id + separator + "payload" + separator + executePath);
        dtoIn.addAll(List.of(commandToExecute.split(" ")));
        dtoIn.addAll(parameters);


        Process process = new ProcessBuilder(dtoIn)
                .directory(workingDir)
                .redirectOutput(new File(resDir.getPath() + separator + "consoleOutput.txt"))
                .redirectError(new File(resDir.getPath() + separator + "errorOutput.txt"))
                .start();
        process.waitFor(timeoutInMillis, TimeUnit.MILLISECONDS);

        List<File> files = new LinkedList<>();
        //try to get results - move them from task
        for (String singlePath : pathToResults) {
            try {
                File resultFile = new File(PATH_TO_TASK_STORAGE.replace('\\', '/') + id + separator + "payload" + separator + singlePath);
                files.add(resultFile);
                //create path
                Files.createDirectories(Paths.get(resDir.getAbsolutePath() + "/" + singlePath));
                //move the file
                Files.move(Paths.get(resultFile.getAbsolutePath()), Paths.get(resDir.getAbsolutePath() + "/" + singlePath), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.out.println("ERROR: Some results couldn't be obtained: task id: " + id + ", path to result: " + singlePath);
                System.out.println(e.getMessage());
            }
        }

        return process.exitValue();
    }
}
