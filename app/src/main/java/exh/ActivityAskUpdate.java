package exh;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatDialog;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import eu.kanade.tachiyomi.BuildConfig;
import eu.kanade.tachiyomi.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ActivityAskUpdate extends AppCompatDialog {

    String details;
    String downloadURL;

    public ActivityAskUpdate(Context context, String details, String downloadURL) {
        super(context);
        this.details = details;
        this.downloadURL = downloadURL;
        setCancelable(false);
        setTitle("New Version Available");
    }

    public static void checkAndDoUpdateIfNeeded(final Context context, boolean isAutoUpdate) {
        final ProgressDialog[] pDialog = {null};
        try {
            //Return immediately if auto update is disabled
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (!preferences.getBoolean("auto_update", true) && isAutoUpdate) return;
            if(!isAutoUpdate) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        pDialog[0] = ProgressDialog.show(context, "Please wait...", "Checking for updates...", true, true);
                    }
                });
            }
            if (!preferences.contains("force_update")) {
                preferences
                        .edit()
                        .putBoolean("force_update", false)
                        .apply();
            }
            Request request = new Request.Builder().url("http://nn9.pe.hu/tyeh/update.php").build();
            OkHttpClient client = NetworkManager.getInstance().getClient();
            Response response;
            try {
                response = client.newCall(request).execute();
            } catch (IOException e) {
                Log.w("EHentai", "Could not check for updates!", e);
                return;
            }
            if (response.isSuccessful()) {
                String responseString;
                try {
                    responseString = response.body().string();
                } catch (IOException e) {
                    Log.w("EHentai", "Could not check for updates!", e);
                    return;
                }
                String[] split = responseString.split("[\\r\\n]+");
                boolean hasUpdateHeader = false;
                String author = "";
                int version = BuildConfig.VERSION_CODE;
                String download = "";
                String description = "";
                for (String line : split) {
                    if (line.contains("<Tachiyomi E-Hentai Update File>")) hasUpdateHeader = true;
                    else {
                        int equalIndex = line.indexOf('=');
                        if(equalIndex == -1) continue;
                        String key = line.substring(0, equalIndex);
                        String value = line.substring(equalIndex + 1);
                        switch (key) {
                            case "Author":
                                author = value;
                                break;
                            case "Version":
                                try {
                                    version = Integer.parseInt(value);
                                } catch (NumberFormatException e) {
                                    Log.e("EHentai", "Exception parsing version number!", e);
                                }
                                break;
                            case "Download":
                                download = value;
                                break;
                            case "Description":
                                description = new String(Base64.decode(value, Base64.NO_WRAP));
                                break;
                        }
                    }
                }
                if ((hasUpdateHeader && version > BuildConfig.VERSION_CODE) || preferences
                        .getBoolean("force_update", false)) {
                    Log.i("EHentai", "Update available, requesting!");
                    final String finalDescription = description;
                    final String finalDownload = download;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            ActivityAskUpdate dialog = new ActivityAskUpdate(context, finalDescription, finalDownload);
                            if (pDialog[0] != null) {
                                pDialog[0].dismiss();
                            }
                            dialog.show();
                        }
                    });
                } else if (!isAutoUpdate) {
                    if(pDialog[0] != null)
                        pDialog[0].dismiss();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override public void run() {
                            new AlertDialog.Builder(context)
                                    .setTitle("Update Checker")
                                    .setMessage("No update found!")
                                    .setPositiveButton("OK!", new OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    }).show();
                        }
                    });
                }
            }
        } catch(Throwable t) {
            Log.e("EHentai", "Update check error!", t);
        } finally {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (pDialog[0] != null)
                        pDialog[0].dismiss();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ((TextView) findViewById(R.id.detailsView)).setText(Html.fromHtml(details));
        findViewById(R.id.downloadBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                //The comments below are for background downloading, background downloading is sort of useless however so to reduce maintenance costs, it has been disabled
                /*new AlertDialog.Builder(ActivityAskUpdate.this.getContext())
                        .setCancelable(false)
                        .setTitle("Download in Background?")
                        .setMessage("Would you like to download the update in the background? " +
                                "This means you can keep reading while the update is downloading! " +
                                "I will notify you when the download is done!")
                        .setPositiveButton("Yes", new OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                ActivityAskUpdate.this.dismiss();
                                ActivityAskUpdate.this.performUpdate(true);
                            }
                        })
                        .setNegativeButton("No", new OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();*/
                                ActivityAskUpdate.this.performUpdate(false);
                /*
                            }
                        })
                        .setNeutralButton("Cancel", new OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                ActivityAskUpdate.this.dismiss();
                            }
                        }).show();*/
            }
        });
        findViewById(R.id.ignoreUpdatesBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                PreferenceManager.getDefaultSharedPreferences(ActivityAskUpdate.this.getContext()).edit().putBoolean("auto_update", false).apply();
                ActivityAskUpdate.this.dismiss();
            }
        });
        findViewById(R.id.cancelBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ActivityAskUpdate.this.dismiss();
            }
        });
    }

    private void notifyBackgroundDownloadDone(File apkFile) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setLargeIcon(BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_file_download_white_24dp))
                .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                .setContentTitle("Update Download Complete")
                .setContentText("Update download complete! Press on me to begin start installation!");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(ActivityAskUpdate.this.getContext().getApplicationContext(),
                0,
                intent,
                0);
        builder.setContentIntent(pendingIntent);
        ((NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, builder.build());
        Log.i("EHentai", "Update download complete!");
    }

    private void notifyBackgroundDownloadFail(String failure) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setLargeIcon(BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_file_download_white_24dp))
                .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                .setContentTitle("Update Download Failed")
                .setContentText(failure);
        ((NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, builder.build());
        Log.i("EHentai", "Update download failed! (" + failure + ")");
    }

    private void performUpdate(final boolean background) {
        final ProgressDialog dialog = new ProgressDialog(getContext());
        dialog.setTitle("Downloading Update");
        dialog.setMessage("Downloading update... (This may take a while)");
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.show();

        doKeepDialog(dialog);
        //Just dismiss it right away if we are downloading in the background
        if (background) {
            dialog.dismiss();
        }


        new Thread(new Runnable() {
            @Override public void run() {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(downloadURL)
                        .build();
                Response response = null;
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    Log.e("EHentai", "Update download failed!", e);
                    e.printStackTrace();
                }
                if (response == null || !response.isSuccessful()) {
                    dialog.dismiss();
                    if (!background) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                new AlertDialog.Builder(ActivityAskUpdate.this.getContext())
                                        .setTitle("Error!")
                                        .setMessage("Could not download update! Please try again later!")
                                        .setNeutralButton("Ok", new OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog1, int which) {
                                                dialog1.dismiss();
                                                ActivityAskUpdate.this.dismiss();
                                            }
                                        }).create().show();
                            }
                        });
                    } else {
                        ActivityAskUpdate.this.notifyBackgroundDownloadFail("Could not download update! Please try again later!");
                    }
                } else {
                    File downloadFolder = getContext().getExternalCacheDir();
                    downloadFolder.mkdirs();
                    final File apkFile = new File(downloadFolder, "teh-autoupdate.apk");
                    if (apkFile.exists())
                        apkFile.delete();
                    try {
                        apkFile.createNewFile();
                        FileOutputStream outputStream = new FileOutputStream(apkFile);
                        InputStream inputStream = response.body().byteStream();
                        int bytesCopied = 0;
                        long lastUpdate = System.currentTimeMillis();
                        byte[] buffer = new byte[1024 * 4];
                        int len = inputStream.read(buffer);
                        while (len != -1) {
                            outputStream.write(buffer, 0, len);
                            len = inputStream.read(buffer);
                            bytesCopied += len;
                            final int finalBytesCopied = bytesCopied;
                            if (lastUpdate < System.currentTimeMillis() - 500) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        dialog.setMessage("Downloading update... (This may take a while) [" + finalBytesCopied + " bytes downloaded]");
                                    }
                                });
                                lastUpdate = System.currentTimeMillis();
                            }
                        }
                        dialog.dismiss();
                        if (!background) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    ActivityAskUpdate.this.dismiss();
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    ActivityAskUpdate.this.getContext().startActivity(intent);
                                }
                            });
                        } else {
                            ActivityAskUpdate.this.notifyBackgroundDownloadDone(apkFile);
                        }
                    } catch (IOException e) {
                        Log.e("EHentai", "APK write failed!", e);
                        e.printStackTrace();
                        dialog.dismiss();
                        if (!background) {
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    new AlertDialog.Builder(ActivityAskUpdate.this.getContext())
                                            .setTitle("Error!")
                                            .setMessage("Could not write APK to sdcard! Do you have enough space?")
                                            .setNeutralButton("Ok", new OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface d, int which) {
                                                    d.dismiss();
                                                    ActivityAskUpdate.this.dismiss();
                                                }
                                            }).create().show();
                                }
                            });
                        } else {
                            ActivityAskUpdate.this.notifyBackgroundDownloadFail("Could not write APK to sdcard! Do you have enough space?");
                        }
                    }
                }
            }
        }).start();
    }

    // Prevent dialog dismiss when orientation changes
    private static void doKeepDialog(Dialog dialog) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    static Handler handler = null;

    public static void runOnUiThread(Runnable r) {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        handler.post(r);
    }
}