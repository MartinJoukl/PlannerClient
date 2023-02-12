package joukl.plannerexec.plannerclient.model;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

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

}
