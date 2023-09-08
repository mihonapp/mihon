package eu.kanade.tachiyomi.source

import android.app.Application
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableSource : Source {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     *
     * @since extensions-lib 1.5
     */
    fun getPreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
