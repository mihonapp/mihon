package eu.kanade.tachiyomi.data.download

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

class MangaDirsExceptTest {

    @Test
    fun `returns only the directories of manga that aren't kept`() {
        mangaDirsExcept(
            sourceDirKeys = mapOf(1L to "source"),
            mangaDirsBySourceDirKey = mapOf("source" to dirs("source", "Kept", "Removed")),
            keptDirNames = mapOf(1L to setOf("Kept")),
        ) shouldContainExactlyInAnyOrder listOf("source/Removed")
    }

    @Test
    fun `keeps a directory kept by another source id sharing the source directory`() {
        mangaDirsExcept(
            sourceDirKeys = mapOf(1L to "shared", 2L to "shared"),
            mangaDirsBySourceDirKey = mapOf("shared" to dirs("shared", "Of source 1", "Of source 2", "Removed")),
            keptDirNames = mapOf(1L to setOf("Of source 1"), 2L to setOf("Of source 2")),
        ) shouldContainExactlyInAnyOrder listOf("shared/Removed")
    }

    @Test
    fun `doesn't keep a name in the directory of another source`() {
        mangaDirsExcept(
            sourceDirKeys = mapOf(1L to "first", 2L to "second"),
            mangaDirsBySourceDirKey = mapOf(
                "first" to dirs("first", "Same title"),
                "second" to dirs("second", "Same title"),
            ),
            keptDirNames = mapOf(1L to setOf("Same title")),
        ) shouldContainExactlyInAnyOrder listOf("second/Same title")
    }

    @Test
    fun `keeps everything when every manga is kept`() {
        mangaDirsExcept(
            sourceDirKeys = mapOf(1L to "source"),
            mangaDirsBySourceDirKey = mapOf("source" to dirs("source", "A", "B")),
            keptDirNames = mapOf(1L to setOf("A", "B")),
        ).shouldBeEmpty()
    }

    private fun dirs(sourceDirKey: String, vararg names: String): Map<String, String> {
        return names.associateWith { "$sourceDirKey/$it" }
    }
}
