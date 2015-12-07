package eu.kanade.mangafeed.ui.setting.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import eu.kanade.mangafeed.data.source.base.Source;
import eu.kanade.mangafeed.util.ToastUtil;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class SourceLoginDialog extends LoginDialogPreference {

    private Source source;

    public SourceLoginDialog(Context context, PreferencesHelper preferences, Source source) {
        super(context, preferences);
        this.source = source;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        title.setText(getContext().getString(R.string.accounts_login_title, source.getName()));

        username.setText(preferences.getSourceUsername(source));
        password.setText(preferences.getSourcePassword(source));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            preferences.setSourceCredentials(source,
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

        requestSubscription = source
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
                        preferences.setSourceCredentials(source, "", "");
                        loginBtn.setProgress(-1);
                    }
                }, error -> {
                    loginBtn.setProgress(-1);
                    loginBtn.setText(R.string.unknown_error);
                });

    }

}
