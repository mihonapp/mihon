package eu.kanade.tachiyomi.ui.security

import android.os.Bundle
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.app_name
import tachiyomi.i18n.unlock_app_title

/**
 * Blank activity with a BiometricPrompt.
 */
class UnlockActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAuthentication(
            stringResource(MR.strings.unlock_app_title, stringResource(MR.strings.app_name)),
            confirmationRequired = false,
            onSuccess = {
                SecureActivityDelegate.unlock()
                finish()
            },
            onError = { error ->
                logcat(LogPriority.ERROR) { error.toString() }
                applicationContext.toast(error.toString())
                finishAffinity()
            },
        )
    }
}
