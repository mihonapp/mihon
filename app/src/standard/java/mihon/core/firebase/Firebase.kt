package mihon.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object Firebase {
    fun setup(context: Context, preference: PrivacyPreferences, scope: CoroutineScope) {
        preference.analytics().changes().onEach { enabled ->
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }.launchIn(scope)
        preference.crashlytics().changes().onEach { enabled ->
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.launchIn(scope)
    }
}
