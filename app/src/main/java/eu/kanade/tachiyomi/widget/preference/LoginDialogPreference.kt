package eu.kanade.tachiyomi.widget.preference

import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceDialogFragmentCompat
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import com.dd.processbutton.iml.ActionProcessButton
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.setting.SettingsActivity
import kotlinx.android.synthetic.main.pref_account_login.view.*
import rx.Subscription

abstract class LoginDialogPreference : PreferenceDialogFragmentCompat() {

    var v: View? = null
        private set

    val preferences: PreferencesHelper
        get() = (activity as SettingsActivity).preferences

    var requestSubscription: Subscription? = null

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        // Hide positive button
        builder.setPositiveButton("", this)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        v = view.apply {
            show_password.setOnCheckedChangeListener { v, isChecked ->
                if (isChecked)
                    password.transformationMethod = null
                else
                    password.transformationMethod = PasswordTransformationMethod()
            }

            login.setMode(ActionProcessButton.Mode.ENDLESS)
            login.setOnClickListener { checkLogin() }

            setCredentialsOnView(this)

            show_password.isEnabled = password.text.isNullOrEmpty()

            password.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun afterTextChanged(s: Editable) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (s.length == 0) {
                        show_password.isEnabled = true
                    }
                }
            })
        }

    }

    override fun onDialogClosed(positiveResult: Boolean) {
        requestSubscription?.unsubscribe()
    }

    protected abstract fun checkLogin()

    protected abstract fun setCredentialsOnView(view: View)

}
