package eu.kanade.tachiyomi.data.io;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class IOHandler {
    /**
     * Get full filepath of build in Android File picker.
     * If Google Drive (or other Cloud service) throw exception and download before loading
     */
    public static String getFilePath(Uri uri, ContentResolver resolver, Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String filePath = "";
                String wholeID = DocumentsContract.getDocumentId(uri);

                //Ugly work around. In sdk version Kitkat or higher external getDocumentId request will have no content://
                if (wholeID.split(":").length == 1)
                    throw new IllegalArgumentException();

                // Split at colon, use second item in the array
                String id = wholeID.split(":")[1];

                String[] column = {MediaStore.Images.Media.DATA};

                // where id is equal to
                String sel = MediaStore.Images.Media._ID + "=?";

                Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        column, sel, new String[]{id}, null);

                int columnIndex = cursor != null ? cursor.getColumnIndex(column[0]) : 0;

                if (cursor != null ? cursor.moveToFirst() : false) {
                    filePath = cursor.getString(columnIndex);
                }
                cursor.close();
                return filePath;
            } else {
                String[] fields = {MediaStore.Images.Media.DATA};

                Cursor cursor = resolver.query(uri, fields, null, null, null);

                if (cursor == null)
                    return null;

                cursor.moveToFirst();
                String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                cursor.close();

                return path;
            }
        } catch (IllegalArgumentException e) {
            //This exception is thrown when Google Drive. Try to download file
            return downloadMediaAndReturnPath(uri, resolver, context);
        }
    }

    private static String getTempFilename(Context context) throws IOException {
        File outputDir = context.getCacheDir();
        File outputFile = File.createTempFile("temp_cover", "0", outputDir);
        return outputFile.getAbsolutePath();
    }

    private static String downloadMediaAndReturnPath(Uri uri, ContentResolver resolver, Context context) {
        if (uri == null) return null;
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r");
            FileDescriptor fd = pfd != null ? pfd.getFileDescriptor() : null;
            input = new FileInputStream(fd);

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
