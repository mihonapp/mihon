package eu.kanade.core.navigation

import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

// TODO: this prevents crashes in nested navigators with transitions not being disposed
// properly. Go back to using vanilla Voyager Screens once fixed upstream.
abstract class Screen : VoyagerScreen {

    override val key: ScreenKey = uniqueScreenKey
}
