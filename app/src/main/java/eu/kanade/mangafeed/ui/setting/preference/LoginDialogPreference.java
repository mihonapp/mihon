package eu.kanade.mangafeed.ui.setting.preference;

import android.app.AlertDialog;
import android.content.Context;
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
import eu.kanade.mangafeed.data.preference.PreferencesHelper;
import rx.Subscription;

public abstract class LoginDialogPreference extends DialogPreference {

    @Bind(R.id.accounts_login) TextView title;
    @Bind(R.id.username) EditText username;
    @Bind(R.id.password) EditText password;
    @Bind(R.id.show_password) CheckBox showPassword;
    @Bind(R.id.login) ActionProcessButton loginBtn;

    protected PreferencesHelper preferences;
    protected AlertDialog dialog;
    protected Subscription requestSubscription;
    protected Context context;

    public LoginDialogPreference(Context context, PreferencesHelper preferences) {
        super(context, null);
        this.context = context;
        this.preferences = preferences;

        setDialogLayoutResource(R.layout.pref_account_login);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        // Hide positive button
        builder.setPositiveButton("", this);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        ButterKnife.bind(this, view);

        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked)
                password.setTransformationMethod(null);
            else
                password.setTransformationMethod(new PasswordTransformationMethod());
        });

        loginBtn.setMode(ActionProcessButton.Mode.ENDLESS);
        loginBtn.setOnClickListener(click -> checkLogin());
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
    }

    protected abstract void checkLogin();

}
