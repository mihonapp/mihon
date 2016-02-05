package eu.kanade.tachiyomi.data.updater;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.inject.Inject;

import eu.kanade.tachiyomi.App;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;

public class UpdateDownloader extends AsyncTask<String, Void, Void> {
    /**
     * Name of cache directory.
     */
    private static final String PARAMETER_CACHE_DIRECTORY = "apk_downloads";
    /**
     * Interface to global information about an application environment.
     */
    private final Context context;
    /**
     * Cache directory used for cache management.
     */
    private final File cacheDir;
    @Inject PreferencesHelper preferencesHelper;

    /**
     * Constructor of UpdaterCache.
     *
     * @param context application environment interface.
     */
    public UpdateDownloader(Context context) {
        App.get(context).getComponent().inject(this);
        this.context = context;

        // Get cache directory from parameter.
        cacheDir = new File(preferencesHelper.getDownloadsDirectory(), PARAMETER_CACHE_DIRECTORY);

        // Create cache directory.
        createCacheDir();
    }

    /**
     * Create cache directory if it doesn't exist
     *
     * @return true if cache dir is created otherwise false.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean createCacheDir() {
        return !cacheDir.exists() && cacheDir.mkdirs();
    }


    @Override
    protected Void doInBackground(String... args) {
        try {
            createCacheDir();

            URL url = new URL(args[0]);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.connect();

            File outputFile = new File(cacheDir, "update.apk");
            if (outputFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            FileOutputStream fos = new FileOutputStream(outputFile);

            InputStream is = c.getInputStream();

            byte[] buffer = new byte[1024];
            int len1;
            while ((len1 = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len1);
            }
            fos.close();
            is.close();

            // Prompt install interface
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(outputFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // without this flag android returned a intent error!
            context.startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

