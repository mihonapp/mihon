package mihon.desktop.navigation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DesktopDestinationTest {

    @Test
    fun `primary destinations preserve the approved desktop order`() {
        DesktopDestination.entries.map { it.label } shouldBe listOf(
            "Library",
            "Updates",
            "History",
            "Browse",
            "Downloads",
            "Stats",
            "Settings",
            "About",
        )
    }

    @Test
    fun `navigator publishes a destination change once`() {
        val changes = mutableListOf<DesktopDestination>()
        val navigator = DesktopNavigator(DesktopDestination.Library, changes::add)

        navigator.navigate(DesktopDestination.Updates)
        navigator.navigate(DesktopDestination.Updates)

        navigator.current shouldBe DesktopDestination.Updates
        changes shouldBe listOf(DesktopDestination.Updates)
    }

    @Test
    fun `reader destination retains an exact positive chapter id and back restores its origin`() {
        val navigator = DesktopNavigator(DesktopDestination.Library) {}

        navigator.navigate(DesktopDestination.Reader(73))

        navigator.current shouldBe DesktopDestination.Reader(73)
        navigator.back()
        navigator.current shouldBe DesktopDestination.Library
        assertThrows<IllegalArgumentException> { DesktopDestination.Reader(0) }
    }
}
