package exh.log

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault

enum class EHLogLevel(val description: String) {
    MINIMAL("critical errors only"),
    EXTRA("log everything"),
    EXTREME("network inspection mode");

    companion object {
        private var curLogLevel: Int? = null

        val currentLogLevel get() = EHLogLevel.values()[curLogLevel!!]

        fun init(context: Context) {
            curLogLevel = PreferencesHelper(context)
                    .eh_logLevel().getOrDefault()
        }

        fun shouldLog(requiredLogLevel: EHLogLevel): Boolean {
            return curLogLevel!! >= requiredLogLevel.ordinal
        }
    }
}