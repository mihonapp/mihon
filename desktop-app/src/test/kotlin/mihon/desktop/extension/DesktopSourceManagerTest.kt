package mihon.desktop.extension

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.extension.builtin.BundledMangaDexSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopSourceManagerTest {

    @Test
    fun `registers bundled MangaDex source and retrieves it by id`(@TempDir tempDir: Path) {
        val dummyProcessManager = WindowsExtensionProcessManager(tempDir.toFile())
        val manager = DesktopSourceManager(installer = null, processManager = dummyProcessManager)

        val sources = manager.getSources()
        sources.any { it.id == BundledMangaDexSource.MANGADEX_SOURCE_ID } shouldBe true

        val mangaDex = manager.findSourceDescriptor(BundledMangaDexSource.MANGADEX_SOURCE_ID)
        mangaDex shouldNotBe null
        mangaDex?.name shouldBe "MangaDex"
    }

    @Test
    fun `throws when querying unknown source with no process manager`() {
        val manager = DesktopSourceManager(installer = null, processManager = null)

        runBlocking {
            assertThrows<IllegalStateException> {
                manager.getPopular(999999L, 1)
            }
        }
    }
}
