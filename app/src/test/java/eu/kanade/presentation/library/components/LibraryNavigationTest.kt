package eu.kanade.presentation.library.components

import android.view.KeyEvent
import eu.kanade.tachiyomi.ui.library.libraryPageDirection
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LibraryNavigationTest {

    @Test
    fun `page keys work with volume navigation disabled`() {
        libraryPageDirection(KeyEvent.KEYCODE_PAGE_UP, false) shouldBe -1
        libraryPageDirection(KeyEvent.KEYCODE_PAGE_DOWN, false) shouldBe 1
        libraryPageDirection(KeyEvent.KEYCODE_VOLUME_UP, false) shouldBe null
        libraryPageDirection(KeyEvent.KEYCODE_VOLUME_DOWN, false) shouldBe null
    }

    @Test
    fun `volume keys have the same direction as page keys when enabled`() {
        libraryPageDirection(KeyEvent.KEYCODE_VOLUME_UP, true) shouldBe -1
        libraryPageDirection(KeyEvent.KEYCODE_VOLUME_DOWN, true) shouldBe 1
        libraryPageDirection(KeyEvent.KEYCODE_DPAD_DOWN, true) shouldBe null
    }

    @Test
    fun `page distance counts complete rows and their spacing`() {
        val rows = listOf(LibraryRow(0, 100), LibraryRow(104, 204), LibraryRow(208, 308))
        libraryPageDistance(rows, 0, 250, 4) shouldBe 208f
    }

    @Test
    fun `partial rows at either edge do not increase the page step`() {
        val rows = listOf(LibraryRow(-20, 80), LibraryRow(84, 184), LibraryRow(188, 288))
        libraryPageDistance(rows, 0, 250, 4) shouldBe 104f
    }

    @Test
    fun `a row taller than the viewport still permits navigation`() {
        libraryPageDistance(listOf(LibraryRow(-10, 400)), 0, 250, 4) shouldBe 414f
        libraryPageDistance(emptyList(), 0, 250) shouldBe 0f
        libraryPageDistance(listOf(LibraryRow(0, 0)), 0, 250) shouldBe 0f
    }
}
