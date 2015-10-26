package eu.kanade.mangafeed.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.dd.processbutton.iml.ActionProcessButton;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.sources.base.Source;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class LoginDialogPreference extends DialogPreference {

    @Bind(R.id.accounts_login)
    TextView title;
    @Bind(R.id.username)
    EditText username;
    @Bind(R.id.password) EditText password;
    @Bind(R.id.show_password)
    CheckBox showPassword;
    @Bind(R.id.login)
    ActionProcessButton loginBtn;

    private PreferencesHelper preferences;
    private Source source;
    private AlertDialog dialog;
    private Subscription requestSubscription;

    public LoginDialogPreference(Context context, PreferencesHelper preferences, Source source) {
        super(context, null);
        this.preferences = preferences;
        this.source = source;

        setDialogLayoutResource(R.layout.pref_account_login);
    }

    @Override
    protected void onBindDialogView(View view) {
        ButterKnife.bind(this, view);

        title.setText(getContext().getString(R.string.accounts_login_title, source.getName()));

        username.setText(preferences.getSourceUsername(source));
        password.setText(preferences.getSourcePassword(source));
        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked)
                password.setTransformationMethod(null);
            else
                password.setTransformationMethod(new PasswordTransformationMethod());
        });

        loginBtn.setMode(ActionProcessButton.Mode.ENDLESS);
        loginBtn.setOnClickListener(v -> checkLogin());

        super.onBindDialogView(view);
    }

    @Override
    public void showDialog(Bundle state) {
        super.showDialog(state);
        dialog = ((AlertDialog) getDialog());
        setSubmitButtonEnabled(false);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (requestSubscription != null)
            requestSubscription.unsubscribe();

        if(!positiveResult)
            return;

        preferences.setSourceCredentials(source,
                username.getText().toString(),
                password.getText().toString());

        super.onDialogClosed(true);
    }

    private void setSubmitButtonEnabled(boolean enabled) {
        if (dialog != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setEnabled(enabled);
        }
    }

    private void checkLogin() {
        if (requestSubscription != null)
            requestSubscription.unsubscribe();

        if (username.getText().length() == 0 || password.getText().length() == 0)
            return;

        loginBtn.setProgress(1);

        requestSubscription = source.login(username.getText().toString(),
                password.getText().toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(logged -> {
                    if (logged) {
                        loginBtn.setProgress(100);
                        loginBtn.setEnabled(false);
                        username.setEnabled(false);
                        password.setEnabled(false);
                        setSubmitButtonEnabled(true);
                    } else {
                        loginBtn.setProgress(-1);
                    }
                }, throwable -> {
                    loginBtn.setProgress(-1);
                    loginBtn.setText("Unknown error");
                });

    }

}
