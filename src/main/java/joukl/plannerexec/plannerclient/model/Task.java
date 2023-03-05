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
    private Integer from;

    private Integer to;
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

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
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

    private List<String> parametrizedValues;

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

    public List<String> getParametrizedValues() {
        return parametrizedValues;
    }

    public void setParametrizedValues(List<String> parametrizedValues) {
        this.parametrizedValues = parametrizedValues;
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
                @JsonProperty("timeout") long timeoutInMillis, @JsonProperty("priority") int priority, @JsonProperty("queue") String queueName,
                @JsonProperty("executePath") String executePath,
                @JsonProperty("parametrizedFrom") Integer parametrizedFrom, @JsonProperty("parametrizedTo") Integer parametrizedTo,
                @JsonProperty("parametrizedValues") List<String> parametrizedValues) {
        this.cost = cost;
        this.name = name;
        this.commandToExecute = commandToExecute;
        this.pathToResults = pathToResults;
        this.timeoutInMillis = timeoutInMillis;
        this.priority = priority;
        this.queue = queueName;
        this.executePath = executePath;
        this.from = parametrizedFrom;
        this.to = parametrizedTo;
        this.parametrizedValues = parametrizedValues;

        this.id = UUID.randomUUID().toString();

        this.status = TaskStatus.UPLOADED;
    }

    public int run() throws IOException, InterruptedException, URISyntaxException {
        //create results location - also with name of command
        File resDir = new File(PATH_TO_TASK_RESULTS_STORAGE + name + separator + id);
        resDir.mkdirs();
        List<String> currentParameters = new LinkedList<>(parameters);
        int parametrizedPosition = currentParameters.indexOf("%%");

        File workingDir = new File(PATH_TO_TASK_STORAGE + id + separator + "payload" + separator + executePath);

        Process process = runProcessAccordingToParametrization(resDir, currentParameters, parametrizedPosition, workingDir);

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

        //npe can't happen
        return process.exitValue();
    }

    private Process runProcessAccordingToParametrization(File resDir, List<String> currentParameters, int parametrizedPosition, File workingDir) throws IOException, InterruptedException {
        Process process = null;
        if (from != null) {
            if (from > to) {
                for (int i = from; i >= to; i--) {
                    ArrayList<String> dtoIn = new ArrayList<>();
                    dtoIn.addAll(List.of(commandToExecute.split(" ")));
                    if (parametrizedPosition > -1) {
                        currentParameters.set(parametrizedPosition, String.valueOf(i));
                    }
                    dtoIn.addAll(currentParameters);

                    process = innerRun(resDir, dtoIn, workingDir);
                }
            } else {
                for (int i = from; i <= to; i++) {
                    ArrayList<String> dtoIn = new ArrayList<>();
                    dtoIn.addAll(List.of(commandToExecute.split(" ")));
                    if (parametrizedPosition > -1) {
                        currentParameters.set(parametrizedPosition, String.valueOf(i));
                    }
                    dtoIn.addAll(currentParameters);

                    process = innerRun(resDir, dtoIn, workingDir);
                }
            }
        } else if (parametrizedValues != null) {
            for (String parametrizedValue : parametrizedValues) {
                ArrayList<String> dtoIn = new ArrayList<>();
                dtoIn.addAll(List.of(commandToExecute.split(" ")));
                if (parametrizedPosition > -1) {
                    currentParameters.set(parametrizedPosition, parametrizedValue);
                }
                dtoIn.addAll(currentParameters);
                process = innerRun(resDir, dtoIn, workingDir);
            }
        } else {
            ArrayList<String> dtoIn = new ArrayList<>();
            dtoIn.addAll(List.of(commandToExecute.split(" ")));
            dtoIn.addAll(currentParameters);
            process = innerRun(resDir, dtoIn, workingDir);
        }
        return process;
    }

    private Process innerRun(File resDir, ArrayList<String> dtoIn, File workingDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(dtoIn)
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(new File(resDir.getPath() + separator + "consoleOutput.txt")))
                .redirectError(ProcessBuilder.Redirect.appendTo(new File(resDir.getPath() + separator + "errorOutput.txt")))
                .start();
        process.waitFor(timeoutInMillis, TimeUnit.MILLISECONDS);
        //call because if we timed out, we want to see throw exception
        process.exitValue();
        return process;
    }

    public static boolean validateCorrectParametrization(Task task) {

        if (task.from == null && task.to == null && (task.parametrizedValues == null || task.parametrizedValues.isEmpty())) {
            // validation is correct if task doesn't contain parametrized value
            return !task.getParameters().contains("%%");
        }
        //we need to check that parameters are present at max only once
        if (task.getParameters().stream().filter((p) -> p.equals("%%")).count() > 1) {
            return false;
        }
        //NOTE - we don't check for existence of %% because we will run process X times based on params - we will just not fill in any params
        //else we need to check that correct parameters are filled
        // we have task from, we have to have task to and parametrized values can't be filled
        if (task.from != null) {
            return task.to != null && (task.parametrizedValues == null || task.parametrizedValues.isEmpty());
        } else {
            //else we have to have the parametrized values filled, but task has to be null
            return task.to == null && task.parametrizedValues != null && !task.parametrizedValues.isEmpty();
        }
    }
}
