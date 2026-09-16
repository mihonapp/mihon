package mihon.desktop.ui

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.navigation.DesktopNavigator
import org.junit.jupiter.api.Test

class ReaderNavigationIntegrationTest {
    @Test
    fun `reading downloaded chapters returns to downloads even after chapter navigation`() {
        val persistedDestinations = mutableListOf<DesktopDestination>()
        val navigator = DesktopNavigator(DesktopDestination.Downloads, persistedDestinations::add)
        navigator.navigate(DesktopDestination.Reader(72))
        navigator.navigate(DesktopDestination.Reader(73))
        navigator.navigate(DesktopDestination.Reader(73))
        navigator.back()
        navigator.current shouldBe DesktopDestination.Downloads
        persistedDestinations.shouldContainExactly()
    }

    @Test
    fun `reader return destination follows the latest entry point`() {
        val navigator = DesktopNavigator(DesktopDestination.Downloads) {}
        navigator.navigate(DesktopDestination.Reader(72))
        navigator.back()
        navigator.navigate(DesktopDestination.History)
        navigator.navigate(DesktopDestination.Reader(73))
        navigator.back()
        navigator.current shouldBe DesktopDestination.History
    }

    @Test
    fun `double launching a chapter keeps one transient reader route and returns to the library detail`() {
        val persistedDestinations = mutableListOf<DesktopDestination>()
        val navigator = DesktopNavigator(DesktopDestination.Library, persistedDestinations::add)

        navigator.navigate(DesktopDestination.Reader(72))
        navigator.navigate(DesktopDestination.Reader(72))

        navigator.current shouldBe DesktopDestination.Reader(72)
        persistedDestinations.shouldContainExactly()
        navigator.back()
        navigator.current shouldBe DesktopDestination.Library
    }
}
