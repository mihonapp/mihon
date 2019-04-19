package exh.log

import android.content.Context
import android.preference.PreferenceManager
import eu.kanade.tachiyomi.data.preference.PreferenceKeys

enum class EHLogLevel(val description: String) {
    MINIMAL("critical errors only"),
    EXTRA("log everything"),
    EXTREME("network inspection mode");

    companion object {
        private var curLogLevel: Int? = null

        val currentLogLevel get() = values()[curLogLevel!!]

        fun init(context: Context) {
            curLogLevel = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PreferenceKeys.eh_logLevel, 0)
        }

        fun shouldLog(requiredLogLevel: EHLogLevel): Boolean {
            return curLogLevel!! >= requiredLogLevel.ordinal
        }
    }
}