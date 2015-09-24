package eu.kanade.mangafeed.util;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

import eu.kanade.mangafeed.R;

public class DialogFactory {

    public static Dialog createSimpleOkErrorDialog(Context context, String title, String message) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.dialog_action_ok, null);
        return alertDialog.create();
    }

    public static Dialog createSimpleErrorDialog(Context context) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_error_title))
                .setMessage(context.getString(R.string.dialog_general_error_Message))
                .setNeutralButton(R.string.dialog_action_ok, null);
        return alertDialog.create();
    }

    public static ProgressDialog createProgressDialog(Context context, String message) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(message);
        return progressDialog;
    }

    public static ProgressDialog createProgressDialog(Context context, @StringRes int messageResoruce) {
        return createProgressDialog(context, context.getString(messageResoruce));
    }

}
