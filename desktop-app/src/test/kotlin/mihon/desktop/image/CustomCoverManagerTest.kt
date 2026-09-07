package mihon.desktop.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CustomCoverManagerTest {

    @Test
    fun `sets, gets, and removes custom cover`(@TempDir tempDir: Path) {
        val manager = CustomCoverManager(tempDir)
        val mangaId = 42L

        assertFalse(manager.hasCustomCover(mangaId))
        assertNull(manager.getCustomCover(mangaId))

        // Create sample cover file
        val sample = tempDir.resolve("my_cover.png")
        Files.writeString(sample, "fake image content")

        val setTarget = manager.setCustomCover(mangaId, sample)
        assertTrue(Files.exists(setTarget))
        assertTrue(manager.hasCustomCover(mangaId))

        val retrieved = manager.getCustomCover(mangaId)
        assertNotNull(retrieved)
        assertEquals(setTarget, retrieved)

        // Remove custom cover
        val removed = manager.removeCustomCover(mangaId)
        assertTrue(removed)
        assertFalse(manager.hasCustomCover(mangaId))
        assertNull(manager.getCustomCover(mangaId))
    }

    @Test
    fun `replaces existing custom cover when setting new one`(@TempDir tempDir: Path) {
        val manager = CustomCoverManager(tempDir)
        val mangaId = 99L

        val sample1 = tempDir.resolve("sample1.jpg")
        Files.writeString(sample1, "content1")
        val sample2 = tempDir.resolve("sample2.png")
        Files.writeString(sample2, "content2")

        manager.setCustomCover(mangaId, sample1)
        assertEquals("jpg", manager.getCustomCover(mangaId)?.fileName?.toString()?.substringAfterLast('.'))

        manager.setCustomCover(mangaId, sample2)
        assertEquals("png", manager.getCustomCover(mangaId)?.fileName?.toString()?.substringAfterLast('.'))
        // Old jpg should have been cleaned up
        assertFalse(Files.exists(manager.customDir.resolve("custom_99.jpg")))
    }
}
