package eu.kanade.mangafeed.util;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public final class DiskUtils {

    private DiskUtils() {
        throw new AssertionError();
    }

    public static String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static File saveBufferedSourceToDirectory(BufferedSource bufferedSource, File directory, String name) throws IOException {
        createDirectory(directory);

        File writeFile = new File(directory, name);
        if (writeFile.exists()) {
            if (writeFile.delete()) {
                writeFile = new File(directory, name);
            } else {
                throw new IOException("Failed Deleting Existing File for Overwrite");
            }
        }

        BufferedSink bufferedSink = null;
        try {
            bufferedSink = Okio.buffer(Okio.sink(writeFile));
            bufferedSink.writeAll(bufferedSource);
        } catch (Exception e) {
            writeFile.delete();
            throw new IOException("Unable to save image");
        } finally {
            if (bufferedSink != null) {
                bufferedSink.close();
            }
        }

        return writeFile;
    }

    public static void deleteFiles(File inputFile) {
        if (inputFile.isDirectory()) {
            for (File childFile : inputFile.listFiles()) {
                deleteFiles(childFile);
            }
        }

        inputFile.delete();
    }

    public static synchronized void createDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed creating directory");
        }

    }

    public static long getDirectorySize(File f) {
        long size = 0;
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                size += getDirectorySize(file);
            }
        } else {
            size=f.length();
        }
        return size;
    }

}

