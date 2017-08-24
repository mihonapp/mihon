package exh.ui.lock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import com.andrognito.pinlockview.PinLockListener
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import kotlinx.android.synthetic.main.activity_lock.view.*
import uy.kohesive.injekt.injectLazy

class LockController : NucleusController<LockPresenter>() {
    override fun inflateView(inflater: LayoutInflater, container: ViewGroup)
        = inflater.inflate(R.layout.activity_lock, container, false)!!

    override fun createPresenter() = LockPresenter()

    override fun getTitle() = "Application locked"

    val prefs: PreferencesHelper by injectLazy()

    override fun onViewCreated(view: View, savedViewState: Bundle?) {
        super.onViewCreated(view, savedViewState)

        if(!lockEnabled(prefs)) {
            closeLock()
            return
        }

        with(view) {
            pin_lock_view.attachIndicatorDots(indicator_dots)

            pin_lock_view.pinLength = prefs.lockLength().getOrDefault()
            pin_lock_view.setPinLockListener(object : PinLockListener {
                override fun onEmpty() {}

                override fun onComplete(pin: String) {
                    if (sha512(pin, prefs.lockSalt().get()!!) == prefs.lockHash().get()) {
                        //Yay!
                        closeLock()
                    } else {
                        MaterialDialog.Builder(context)
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
    }

    fun closeLock() {
        router.popCurrentController()
    }

    override fun handleBack() = true
}
