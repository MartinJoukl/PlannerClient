package joukl.plannerexec.plannerclient.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.io.File.separator;
import static joukl.plannerexec.plannerclient.model.Client.PATH_TO_TASK_RESULTS_STORAGE;

public class Persistence {

    public static boolean saveBytesToFile(Path pathWithFileName, byte[] bytes) {
        try (FileOutputStream outputStream = new FileOutputStream(pathWithFileName.toString())) {
            outputStream.write(bytes);
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    // Source:
    // https://www.baeldung.com/java-compress-and-uncompress
    public static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    //https://www.baeldung.com/java-compress-and-uncompress
    //Unzips task to task folder
    public static void unzip(Task task) throws IOException {
        String fileZip = task.getPathToZip();
        File destDir = new File(Client.PATH_TO_TASK_STORAGE);

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }

    //https://www.baeldung.com/java-compress-and-uncompress
    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * @param task
     * @param configFile
     * @return Original task merged with task from configuration
     * @throws IOException
     */
    public static Task mergeTaskWithConfiguration(Task task, File configFile) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        try {
            Task fromConfig = mapper
                    .readerFor(Task.class)
                    .readValue(configFile);

            task.setCost(fromConfig.getCost());
            task.setName(fromConfig.getName());
            task.setCommandToExecute(fromConfig.getCommandToExecute());
            task.setPathToResults(fromConfig.getPathToResults());
            task.setTimeoutInMillis(fromConfig.getTimeoutInMillis());
            task.setParameters(fromConfig.getParameters());
            task.setPriority(fromConfig.getPriority());
            task.setExecutePath(fromConfig.getExecutePath());

            task.setQueue(fromConfig.getQueue());

            return task;

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void cleanUp(Task task) throws IOException {
        //delete res
        Files.delete(Path.of(Client.PATH_TO_TASK_RESULTS_STORAGE + task.getName() + separator + task.getId() + ".zip"));
        //SO!
        //https://stackoverflow.com/questions/35988192/java-nio-most-concise-recursive-directory-delete
        try (Stream<Path> walk = Files.walk(Path.of(PATH_TO_TASK_RESULTS_STORAGE + task.getName() + separator + task.getId()))) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }

        //SO!
        //https://stackoverflow.com/questions/35988192/java-nio-most-concise-recursive-directory-delete
        try (Stream<Path> walk = Files.walk(Path.of(Client.PATH_TO_TASK_STORAGE + task.getId()))) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }

        Files.delete(Path.of(Client.PATH_TO_TASK_STORAGE + task.getId() + ".zip"));
    }

}
