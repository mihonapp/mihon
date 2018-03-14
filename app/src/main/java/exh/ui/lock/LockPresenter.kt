package exh.ui.lock

import android.os.Build
import com.github.ajalt.reprint.core.Reprint
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import uy.kohesive.injekt.injectLazy

class LockPresenter: BasePresenter<LockController>() {
    val prefs: PreferencesHelper by injectLazy()

    val useFingerprint
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Reprint.isHardwarePresent()
                && Reprint.hasFingerprintRegistered()
                && prefs.eh_lockUseFingerprint().getOrDefault()
}

