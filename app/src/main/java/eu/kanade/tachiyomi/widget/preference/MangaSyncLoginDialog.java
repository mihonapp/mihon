package eu.kanade.tachiyomi.widget.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import eu.kanade.tachiyomi.R;
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService;
import eu.kanade.tachiyomi.data.preference.PreferencesHelper;
import eu.kanade.tachiyomi.util.ToastUtil;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MangaSyncLoginDialog extends LoginDialogPreference {

    private MangaSyncService sync;

    public MangaSyncLoginDialog(Context context, PreferencesHelper preferences, MangaSyncService sync) {
        super(context, preferences);
        this.sync = sync;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        title.setText(getContext().getString(R.string.accounts_login_title, sync.getName()));

        username.setText(preferences.getMangaSyncUsername(sync));
        password.setText(preferences.getMangaSyncPassword(sync));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            preferences.setMangaSyncCredentials(sync,
                    username.getText().toString(),
                    password.getText().toString());
        }
    }

    protected void checkLogin() {
        if (requestSubscription != null)
            requestSubscription.unsubscribe();

        if (username.getText().length() == 0 || password.getText().length() == 0)
            return;

        loginBtn.setProgress(1);

        requestSubscription = sync
                .login(username.getText().toString(), password.getText().toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(logged -> {
                    if (logged) {
                        // Simulate a positive button click and dismiss the dialog
                        onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                        ToastUtil.showShort(context, R.string.login_success);
                    } else {
                        preferences.setMangaSyncCredentials(sync, "", "");
                        loginBtn.setProgress(-1);
                    }
                }, error -> {
                    loginBtn.setProgress(-1);
                    loginBtn.setText(R.string.unknown_error);
                });

    }

}
