package mihon.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.flow.onEach

object Firebase {
    fun setup(context: Context, preference: SecurityPreferences) {
        preference.analytics().changes().onEach { enabled ->
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }
        preference.crashlytics().changes().onEach { enabled ->
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }
    }
}
