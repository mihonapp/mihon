package eu.kanade.tachiyomi.source

import android.content.Context
import android.content.SharedPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableSource : Source {

    /**
     * Gets instance of [SharedPreferences] scoped to the specific source.
     *
     * @since extensions-lib 1.5
     */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Context>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen)
}

fun ConfigurableSource.preferenceKey(): String = "source_$id"

// TODO: use getSourcePreferences once all extensions are on ext-lib 1.5
fun ConfigurableSource.sourcePreferences(): SharedPreferences =
    Injekt.get<Context>().getSharedPreferences(preferenceKey(), Context.MODE_PRIVATE)

fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Context>().getSharedPreferences(key, Context.MODE_PRIVATE)
