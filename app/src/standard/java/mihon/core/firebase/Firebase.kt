package mihon.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import eu.kanade.domain.base.BasePreferences

object Firebase {
    fun setup(context: Context, preference: BasePreferences) {
        preference.incognitoMode().changes().onEach { enabled ->
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        }
    }
}
