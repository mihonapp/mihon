package mihon.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.CoroutineScope

object Firebase {
    fun setup(context: Context, preference: SecurityPreferences, scope: CoroutineScope) {
        preference.analytics().changes().onEach { enabled ->
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }.launchIn(scope)
        preference.crashlytics().changes().onEach { enabled ->
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }.launchIn(scope)
    }
}
