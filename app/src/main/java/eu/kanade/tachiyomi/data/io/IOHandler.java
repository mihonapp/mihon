package eu.kanade.tachiyomi.data.io;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class IOHandler {
    private static String getTempFilename(Context context) throws IOException {
        File outputDir = context.getCacheDir();
        File outputFile = File.createTempFile("temp_cover", "0", outputDir);
        return outputFile.getAbsolutePath();
    }

    public static String downloadMediaAndReturnPath(FileInputStream input, Context context) {
        FileOutputStream output = null;
        try {

            String tempFilename = getTempFilename(context);
            output = new FileOutputStream(tempFilename);

            int read;
            byte[] bytes = new byte[4096];
            while ((read = input.read(bytes)) != -1) {
                output.write(bytes, 0, read);
            }
            return tempFilename;
        } catch (IOException ignored) {
        } finally {
            if (input != null) try {
                input.close();
            } catch (Exception ignored) {
            }
            if (output != null) try {
                output.close();
            } catch (Exception ignored) {
            }
        }
        return null;

    }

}
