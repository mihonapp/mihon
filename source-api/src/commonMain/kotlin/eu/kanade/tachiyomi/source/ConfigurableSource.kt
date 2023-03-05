package eu.kanade.tachiyomi.source

interface ConfigurableSource : Source {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
