package mihon.desktop.navigation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopDestinationTest {

    @Test
    fun `primary destinations preserve the approved desktop order`() {
        DesktopDestination.entries.map { it.label } shouldBe listOf(
            "Library",
            "Updates",
            "History",
            "Browse",
            "Downloads",
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
}
