package eu.kanade.mangafeed.ui.setting.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.chaptersync.BaseChapterSync;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.util.ToastUtil;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ChapterSyncLoginDialog extends LoginDialogPreference {

    private BaseChapterSync sync;

    public ChapterSyncLoginDialog(Context context, PreferencesHelper preferences, BaseChapterSync sync) {
        super(context, preferences);
        this.sync = sync;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        title.setText(getContext().getString(R.string.accounts_login_title, sync.getName()));

        username.setText(preferences.getChapterSyncUsername(sync));
        password.setText(preferences.getChapterSyncPassword(sync));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            preferences.setChapterSyncCredentials(sync,
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
                        preferences.setChapterSyncCredentials(sync, "", "");
                        loginBtn.setProgress(-1);
                    }
                }, error -> {
                    loginBtn.setProgress(-1);
                    loginBtn.setText(R.string.unknown_error);
                });

    }

}
