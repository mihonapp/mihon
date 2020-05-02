package exh.ui.lock

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.preference.onChange
import java.math.BigInteger
import java.security.SecureRandom
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy

class LockPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchPreferenceCompat(context, attrs) {

    private val secureRandom by lazy { SecureRandom() }

    val prefs: PreferencesHelper by injectLazy()

    override fun onAttached() {
        super.onAttached()
        updateSummary()
        onChange {
            tryChange()
            false
        }
    }

    private fun updateSummary() {
        isChecked = lockEnabled(prefs)
        if (isChecked) {
            title = "Lock enabled"
            summary = "Tap to disable or change pin code"
        } else {
            title = "Lock disabled"
            summary = "Tap to enable"
        }
    }

    fun tryChange() {
        if (!notifyLockSecurity(context)) {
            MaterialDialog(context)
                .title(text = "Lock application")
                .message(text = "Enter a pin to lock the application. Enter nothing to disable the pin lock.")
                // .inputRangeRes(0, 10, R.color.material_red_500)
                // .inputType(InputType.TYPE_CLASS_NUMBER)
                .input(maxLength = 10, inputType = InputType.TYPE_CLASS_NUMBER, allowEmpty = true) { _, c ->
                    val progressDialog = MaterialDialog(context)
                        .title(text = "Saving password")
                        .cancelable(false)
                    progressDialog.show()
                    Observable.fromCallable {
                        savePassword(c.toString())
                    }.subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            progressDialog.dismiss()
                            updateSummary()
                        }
                }
                .negativeButton(R.string.action_cancel)
                .cancelable(true)
                .cancelOnTouchOutside(true)
                .show()
        }
    }

    private fun savePassword(password: String) {
        val salt: String?
        val hash: String?
        val length: Int
        if (password.isEmpty()) {
            salt = null
            hash = null
            length = -1
        } else {
            salt = BigInteger(130, secureRandom).toString(32)
            hash = sha512(password, salt)
            length = password.length
        }
        prefs.eh_lockSalt().set(salt)
        prefs.eh_lockHash().set(hash)
        prefs.eh_lockLength().set(length)
    }
}
