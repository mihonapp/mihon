package exh.ui.lock

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v7.preference.SwitchPreferenceCompat
import android.support.v7.widget.LinearLayoutCompat
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.github.ajalt.reprint.core.AuthenticationResult
import com.github.ajalt.reprint.core.Reprint
import com.github.ajalt.reprint.rxjava.RxReprint
import com.mattprecious.swirl.SwirlView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.setting.onChange
import exh.util.dpToPx
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.injectLazy

class FingerLockPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        SwitchPreferenceCompat(context, attrs) {

    val prefs: PreferencesHelper by injectLazy()

    val fingerprintSupported
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Reprint.isHardwarePresent()
                && Reprint.hasFingerprintRegistered()

    val useFingerprint
        get() = fingerprintSupported
                && prefs.eh_lockUseFingerprint().getOrDefault()

    @SuppressLint("NewApi")
    override fun onAttached() {
        super.onAttached()
        if(fingerprintSupported) {
            updateSummary()
            onChange {
                if(it as Boolean)
                    tryChange()
                else
                    prefs.eh_lockUseFingerprint().set(false)
                !it
            }
        } else {
            title = "Fingerprint unsupported"
            shouldDisableView = true
            summary = if(!Reprint.hasFingerprintRegistered())
                "No fingerprints enrolled!"
            else
                "Fingerprint unlock is unsupported on this device!"
            onChange { false }
        }
    }

    private fun updateSummary() {
        isChecked = useFingerprint
        title = if(isChecked)
            "Fingerprint enabled"
        else
            "Fingerprint disabled"
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun tryChange() {
        val statusTextView = TextView(context).apply {
            text = "Please touch the fingerprint sensor"
            val size = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams = (layoutParams ?: ViewGroup.LayoutParams(
                    size, size
            )).apply {
                width = size
                height = size
                setPadding(0, 0, dpToPx(context, 8), 0)
            }
        }
        val iconView = SwirlView(context).apply {
            val size = dpToPx(context, 30)
            layoutParams = (layoutParams ?: ViewGroup.LayoutParams(
                    size, size
            )).apply {
                width = size
                height = size
            }
            setState(SwirlView.State.OFF, false)
        }
        val linearLayout = LinearLayoutCompat(context).apply {
            orientation = LinearLayoutCompat.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val size = LinearLayoutCompat.LayoutParams.WRAP_CONTENT
            layoutParams = (layoutParams ?: LinearLayoutCompat.LayoutParams(
                    size, size
            )).apply {
                width = size
                height = size
                val pSize = dpToPx(context, 24)
                setPadding(pSize, 0, pSize, 0)
            }

            addView(statusTextView)
            addView(iconView)
        }
        val dialog = MaterialDialog.Builder(context)
                .title("Fingerprint verification")
                .customView(linearLayout, false)
                .negativeText("Cancel")
                .autoDismiss(true)
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .show()
        iconView.setState(SwirlView.State.ON)
        val subscription = RxReprint.authenticate()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { result ->
                    when (result.status) {
                        AuthenticationResult.Status.SUCCESS -> {
                            iconView.setState(SwirlView.State.ON)
                            prefs.eh_lockUseFingerprint().set(true)
                            dialog.dismiss()
                            updateSummary()
                        }
                        AuthenticationResult.Status.NONFATAL_FAILURE -> {
                            iconView.setState(SwirlView.State.ERROR)
                            statusTextView.text = result.errorMessage
                        }
                        AuthenticationResult.Status.FATAL_FAILURE, null -> {
                            MaterialDialog.Builder(context)
                                    .title("Fingerprint verification failed!")
                                    .content(result.errorMessage)
                                    .positiveText("Ok")
                                    .autoDismiss(true)
                                    .cancelable(true)
                                    .canceledOnTouchOutside(false)
                                    .show()
                            dialog.dismiss()
                        }
                    }
                }
        dialog.setOnDismissListener {
            subscription.unsubscribe()
        }
    }
}