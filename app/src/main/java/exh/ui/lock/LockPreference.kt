package exh.ui.lock

import android.content.Context
import android.support.v7.preference.Preference
import android.text.InputType
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.math.BigInteger
import java.security.SecureRandom

class LockPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Preference(context, attrs) {

    val secureRandom by lazy { SecureRandom() }

    val prefs: PreferencesHelper by injectLazy()

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    fun updateSummary() {
        if(lockEnabled(prefs)) {
            summary = "Application is locked"
        } else {
            summary = "Application is not locked, tap to lock"
        }
    }

    override fun onClick() {
        super.onClick()
        if(!notifyLockSecurity(context)) {
            MaterialDialog.Builder(context)
                    .title("Lock application")
                    .content("Enter a pin to lock the application. Enter nothing to disable the pin lock.")
                    .inputRangeRes(0, 10, R.color.material_red_500)
                    .inputType(InputType.TYPE_CLASS_NUMBER)
                    .input("", "", { _, c ->
                        val progressDialog = MaterialDialog.Builder(context)
                                .title("Saving password")
                                .progress(true, 0)
                                .cancelable(false)
                                .show()
                        Observable.fromCallable {
                            savePassword(c.toString())
                        }.subscribeOn(Schedulers.computation())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    progressDialog.dismiss()
                                    updateSummary()
                                }
                    })
                    .negativeText("Cancel")
                    .autoDismiss(true)
                    .cancelable(true)
                    .canceledOnTouchOutside(true)
                    .show()
        }
    }

    fun savePassword(password: String) {
        val salt: String?
        val hash: String?
        val length: Int
        if(password.isEmpty()) {
            salt = null
            hash = null
            length = -1
        } else {
            salt = BigInteger(130, secureRandom).toString(32)
            hash = sha512(password, salt)
            length = password.length
        }
        prefs.lockSalt().set(salt)
        prefs.lockHash().set(hash)
        prefs.lockLength().set(length)
    }
}
