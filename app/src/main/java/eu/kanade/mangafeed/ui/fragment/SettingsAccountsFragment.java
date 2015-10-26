package eu.kanade.mangafeed.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import eu.kanade.mangafeed.App;
import eu.kanade.mangafeed.R;
import eu.kanade.mangafeed.data.helpers.PreferencesHelper;
import eu.kanade.mangafeed.data.helpers.SourceManager;
import eu.kanade.mangafeed.sources.Source;
import eu.kanade.mangafeed.ui.activity.base.BaseActivity;
import rx.Observable;

public class SettingsAccountsFragment extends PreferenceFragment {

    @Inject SourceManager sourceManager;
    @Inject PreferencesHelper preferences;

    public static SettingsAccountsFragment newInstance() {
        return new SettingsAccountsFragment();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.get(getActivity()).getComponent().inject(this);

        addPreferencesFromResource(R.xml.pref_accounts);

        PreferenceScreen screen = getPreferenceScreen();

        List<Source> sourceAccounts = getSourcesWithLogin();

        for (Source source : sourceAccounts) {
            LoginDialogPreference dialog = new LoginDialogPreference(
                    screen.getContext(), null, source);
            dialog.setTitle(source.getName());

            screen.addPreference(dialog);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseActivity)getActivity())
                .setToolbarTitle(getString(R.string.pref_category_accounts));
    }

    private List<Source> getSourcesWithLogin() {
        return Observable.from(sourceManager.getSources())
                .filter(Source::isLoginRequired)
                .toList()
                .toBlocking()
                .single();
    }

    public class LoginDialogPreference extends DialogPreference {

        @Bind(R.id.accounts_login) TextView title;
        @Bind(R.id.username) EditText username;
        @Bind(R.id.password) EditText password;
        @Bind(R.id.show_password) CheckBox showPassword;

        private Source source;

        public LoginDialogPreference(Context context, AttributeSet attrs, Source source) {
            super(context, attrs);
            this.source = source;

            setDialogLayoutResource(R.layout.pref_account_login);
        }

        @Override
        protected void onBindDialogView(View view) {
            ButterKnife.bind(this, view);

            title.setText(getString(R.string.accounts_login_title, source.getName()));

            username.setText(preferences.getSourceUsername(source));
            password.setText(preferences.getSourcePassword(source));
            showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked)
                    password.setTransformationMethod(null);
                else
                    password.setTransformationMethod(new PasswordTransformationMethod());
            });

            super.onBindDialogView(view);
        }

        @Override
        protected void onDialogClosed(boolean positiveResult) {
            if(!positiveResult)
                return;

            preferences.setSourceCredentials(source,
                    username.getText().toString(),
                    password.getText().toString());

            super.onDialogClosed(true);
        }

    }

}
