package eu.kanade.tachiyomi.util.system

import android.content.Context
import androidx.annotation.CallSuper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationError
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import kotlin.coroutines.resume

object AuthenticatorUtil {

    /**
     * A check to avoid double authentication on older APIs when confirming settings changes since
     * the biometric prompt is launched in a separate activity outside of the app.
     */
    var isAuthenticating = false

    /**
     * Launches biometric prompt.
     *
     * @param title String title that will be shown on the prompt
     * @param subtitle Optional string subtitle that will be shown on the prompt
     * @param confirmationRequired Whether require explicit user confirmation after passive biometric is recognized
     * @param callback Callback object to handle the authentication events
     */
    fun FragmentActivity.startAuthentication(
        title: String,
        subtitle: String? = null,
        confirmationRequired: Boolean = true,
        callback: AuthenticationCallback,
    ) {
        isAuthenticating = true
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setConfirmationRequired(confirmationRequired)
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    callback.onAuthenticationError(this@startAuthentication, errorCode, errString)
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback.onAuthenticationSucceeded(this@startAuthentication, result)
                }
                override fun onAuthenticationFailed() {
                    callback.onAuthenticationFailed(this@startAuthentication)
                }
            },
        )
        biometricPrompt.authenticate(promptInfo)
    }

    suspend fun FragmentActivity.authenticate(
        title: String,
        subtitle: String? = stringResource(MR.strings.confirm_lock_change),
    ): Boolean = suspendCancellableCoroutine { cont ->
        if (!isAuthenticationSupported()) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }

        startAuthentication(
            title,
            subtitle,
            callback = object : AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    activity: FragmentActivity?,
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(activity, result)
                    cont.resume(true)
                }

                override fun onAuthenticationError(
                    activity: FragmentActivity?,
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(activity, errorCode, errString)
                    activity?.toast(errString.toString())
                    cont.resume(false)
                }
            },
        )
    }

    /**
     * Returns true if Class 2 biometric or credential lock is set and available to use
     */
    fun Context.isAuthenticationSupported(): Boolean {
        val authenticators = Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Custom callback with extra check
     *
     * @see isAuthenticating
     */
    abstract class AuthenticationCallback {
        @CallSuper
        open fun onAuthenticationError(
            activity: FragmentActivity?,
            @AuthenticationError errorCode: Int,
            errString: CharSequence,
        ) {
            isAuthenticating = false
        }

        @CallSuper
        open fun onAuthenticationSucceeded(
            activity: FragmentActivity?,
            result: BiometricPrompt.AuthenticationResult,
        ) {
            isAuthenticating = false
        }

        open fun onAuthenticationFailed(activity: FragmentActivity?) {}
    }
}
