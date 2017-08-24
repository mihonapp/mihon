package exh.ui.lock

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.andrognito.pinlockview.PinLockListener
import com.github.ajalt.reprint.core.AuthenticationResult
import com.github.ajalt.reprint.core.Reprint
import com.github.ajalt.reprint.rxjava.RxReprint
import com.mattprecious.swirl.SwirlView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.main.MainActivity
import exh.util.dpToPx
import kotlinx.android.synthetic.main.activity_lock.view.*
import kotlinx.android.synthetic.main.main_activity.view.*
import uy.kohesive.injekt.injectLazy

class LockController : NucleusController<LockPresenter>() {

    val prefs: PreferencesHelper by injectLazy()

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup)
        = inflater.inflate(R.layout.activity_lock, container, false)!!

    override fun createPresenter() = LockPresenter()

    override fun getTitle() = "Application locked"

    override fun onViewCreated(view: View, savedViewState: Bundle?) {
        super.onViewCreated(view, savedViewState)

        if(!lockEnabled(prefs)) {
            closeLock()
            return
        }

        with(view) {
            //Setup pin lock
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

    @SuppressLint("NewApi")
    override fun onAttach(view: View) {
        super.onAttach(view)

        with(view) {
            //Fingerprint
            if (presenter.useFingerprint) {
                swirl_container.removeAllViews()
                val icon = SwirlView(context).apply {
                    val size = dpToPx(context, 60)
                    layoutParams = (layoutParams ?: ViewGroup.LayoutParams(
                            size, size
                    )).apply {
                        width = size
                        height = size

                        val pSize = dpToPx(context, 8)
                        setPadding(pSize, pSize, pSize, pSize)
                    }
                    val typedVal = TypedValue()
                    activity!!.theme!!.resolveAttribute(android.R.attr.windowBackground, typedVal, true)
                    setBackgroundColor(typedVal.data)
                    //Disable elevation if dark theme is active
                    if (typedVal.data == resources.getColor(R.color.backgroundDark, activity!!.theme!!))
                        this@with.swirl_container.cardElevation = 0f
                    setState(SwirlView.State.OFF, false)
                }
                swirl_container.addView(icon)
                icon.setState(SwirlView.State.ON)
                RxReprint.authenticate()
                        .subscribeUntilDetach {
                            when (it.status) {
                                AuthenticationResult.Status.SUCCESS -> closeLock()
                                AuthenticationResult.Status.NONFATAL_FAILURE -> icon.setState(SwirlView.State.ERROR)
                                AuthenticationResult.Status.FATAL_FAILURE, null -> {
                                    MaterialDialog.Builder(context)
                                            .title("Fingerprint error!")
                                            .content(it.errorMessage)
                                            .cancelable(false)
                                            .canceledOnTouchOutside(false)
                                            .positiveText("Ok")
                                            .autoDismiss(true)
                                            .show()
                                    icon.setState(SwirlView.State.OFF)
                                }
                            }
                        }
            }
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
    }

    fun closeLock() {
        router.popCurrentController()
    }

    override fun handleBack() = true
}
