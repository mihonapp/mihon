package exh;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.jakewharton.processphoenix.ProcessPhoenix;

import eu.kanade.tachiyomi.data.source.online.english.EHentai;
import eu.kanade.tachiyomi.ui.main.MainActivity;

public class ExHentaiLoginPref extends SwitchPreferenceCompat {

    public ExHentaiLoginPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    public ExHentaiLoginPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public ExHentaiLoginPref(Context context) {
        super(context);
        setup();
    }

    void setup() {
        enableListeners();
    }

    void disableListeners() {
        setOnPreferenceChangeListener(null);
    }

    void forceAppRestart() {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("App Restart Required")
                .setMessage("An app restart is required to apply changes. Press the 'RESTART' button to restart the application now.")
                .setCancelable(false)
                .setPositiveButton("Restart", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        ProgressDialog progressDialog = ProgressDialog.show(getContext(), "Restarting App", "Please wait...", true, false);
                        doKeepDialog(progressDialog);
                        Intent intent = new Intent(getContext(), MainActivity.class);
                        ProcessPhoenix.triggerRebirth(getContext(), intent);
                    }
                }).show();

        doKeepDialog(dialog);
    }

    private static void doKeepDialog(Dialog dialog){
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(lp);
    }

    void enableListeners() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
                final Context context = ExHentaiLoginPref.this.getContext();

                if ((Boolean) newValue) {
                    EHentai.performLogout(context);
                    new Thread(new Runnable() {
                        @Override public void run() {
                            DialogLogin.requestLogin(context);
                            final boolean isLoggedIn = DialogLogin.isLoggedIn(context, true);
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override public void run() {
                                    if (isLoggedIn) {
                                        ExHentaiLoginPref.this.quietSetChecked(true);
                                        forceAppRestart();
                                    } else {
                                        Toast.makeText(context, "Login failed, please try again!", Toast.LENGTH_LONG).show();
                                    }
                                }
                            });

                        }
                    }).start();
                    return false;
                } else {
                    EHentai.performLogout(context);
                    forceAppRestart();
                    return true;
                }
            }
        });
    }

    void quietSetChecked(boolean checked) {
        disableListeners();
        Log.i("EHentai", "Setting checked...");
        setChecked(checked);
        enableListeners();
    }
}