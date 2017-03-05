package exh.ui.lock

import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.andrognito.pinlockview.PinLockListener
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.nullGetOrDefault
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import kotlinx.android.synthetic.main.activity_lock.*
import uy.kohesive.injekt.injectLazy

class LockActivity : BaseActivity() {

    val prefs: PreferencesHelper by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        disableLock = true

        setTheme(R.style.Theme_Tachiyomi_Dark)
        super.onCreate(savedInstanceState)

        if(!lockEnabled(prefs)) {
            finish()
            return
        }

        setContentView(R.layout.activity_lock)

        pin_lock_view.attachIndicatorDots(indicator_dots)

        pin_lock_view.pinLength = prefs.lockLength().nullGetOrDefault()!!
        pin_lock_view.setPinLockListener(object : PinLockListener {
            override fun onEmpty() {}

            override fun onComplete(pin: String) {
                if(sha512(pin, prefs.lockSalt().nullGetOrDefault()!!) == prefs.lockHash().nullGetOrDefault()) {
                    //Yay!
                    finish()
                } else {
                    MaterialDialog.Builder(this@LockActivity)
                            .title("PIN code incorrect")
                            .content("The PIN code you entered is incorrect. Please try again.")
                            .cancelable(true)
                            .canceledOnTouchOutside(true)
                            .positiveText("Ok")
                            .autoDismiss(true)
                            .show()
                    pin_lock_view.resetPinLockView()
                }
            }

            override fun onPinChange(pinLength: Int, intermediatePin: String?) {}
        })
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
