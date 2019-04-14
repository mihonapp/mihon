package exh.log

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class EHLogLevel(val description: String) {
    MINIMAL("critical errors only"),
    EXTRA("log everything"),
    EXTREME("network inspection mode");

    companion object {
        private val curLogLevel by lazy {
            Injekt.get<PreferencesHelper>().eh_logLevel().getOrDefault()
        }

        fun shouldLog(requiredLogLevel: EHLogLevel): Boolean {
            return curLogLevel >= requiredLogLevel.ordinal
        }
    }
}