package mihon.telemetry

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context) {
        // To stop forks/test builds from polluting our data
        if (!context.isMihonProductionApp()) return

        // Check if Google Play Services is available before initializing Firebase
        if (!isGooglePlayServicesAvailable(context)) {
            logcat(LogPriority.WARN) { "Google Play Services not available, skipping Firebase initialization" }
            return
        }

        try {
            analytics = FirebaseAnalytics.getInstance(context)
            FirebaseApp.initializeApp(context)
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize Firebase" }
        }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            resultCode == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Unable to check Google Play Services availability" }
            false
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    private fun Context.isMihonProductionApp(): Boolean {
        if (packageName !in MIHON_PACKAGES) return false

        return packageManager.getPackageInfo(packageName, SignatureFlags)
            .getCertificateFingerprints()
            .any { it == MIHON_CERTIFICATE_FINGERPRINT }
    }
}

private val MIHON_PACKAGES = hashSetOf("app.mihon", "app.mihon.debug")
private const val MIHON_CERTIFICATE_FINGERPRINT =
    "9A:DD:65:5A:78:E9:6C:4E:C7:A5:3E:F8:9D:CC:B5:57:CB:5D:76:74:89:FA:C5:E7:85:D6:71:A5:A7:5D:4D:A2"
