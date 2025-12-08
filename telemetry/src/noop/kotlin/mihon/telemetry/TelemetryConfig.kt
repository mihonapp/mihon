package mihon.telemetry

import android.content.Context

@Suppress("UNUSED_PARAMETER")
object TelemetryConfig {
    fun init(context: Context) = Unit

    fun setAnalyticsEnabled(enabled: Boolean) = Unit

    fun setCrashlyticsEnabled(enabled: Boolean) = Unit
}
