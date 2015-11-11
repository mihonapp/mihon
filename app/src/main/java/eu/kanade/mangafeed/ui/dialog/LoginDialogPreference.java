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
import eu.kanade.mangafeed.util.ToastUtil;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class LoginDialogPreference extends DialogPreference {

    @Bind(R.id.accounts_login) TextView title;
    @Bind(R.id.username) EditText username;
    @Bind(R.id.password) EditText password;
    @Bind(R.id.show_password) CheckBox showPassword;
    @Bind(R.id.login) ActionProcessButton loginBtn;

    private PreferencesHelper preferences;
    private Source source;
    private AlertDialog dialog;
    private Subscription requestSubscription;
    private Context context;

    public LoginDialogPreference(Context context, PreferencesHelper preferences, Source source) {
        super(context, null);
        this.context = context;
        this.preferences = preferences;
        this.source = source;

        setDialogLayoutResource(R.layout.pref_account_login);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        // Hide positive button
        builder.setPositiveButton("", this);
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
        loginBtn.setOnClickListener(click -> checkLogin());

        super.onBindDialogView(view);
    }

    @Override
    public void showDialog(Bundle state) {
        super.showDialog(state);
        dialog = ((AlertDialog) getDialog());
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
    }

    private void checkLogin() {
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
                        loginBtn.setProgress(-1);
                    }
                }, throwable -> {
                    loginBtn.setProgress(-1);
                    loginBtn.setText(R.string.unknown_error);
                });

    }

}
