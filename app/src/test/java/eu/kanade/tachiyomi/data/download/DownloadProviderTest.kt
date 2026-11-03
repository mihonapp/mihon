package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences

class DownloadProviderTest {

    private fun provider(disallowNonAscii: Boolean): DownloadProvider {
        val preferences = LibraryPreferences(InMemoryPreferenceStore())
        preferences.disallowNonAsciiFilenames.set(disallowNonAscii)
        preferences.enableChapterNameHash.set(true)
        return DownloadProvider(mockk(relaxed = true), mockk(relaxed = true), preferences)
    }

    private fun dirNameBefore0210(name: String, scanlator: String?, url: String, disallowNonAscii: Boolean): String {
        val dirName = if (scanlator.isNullOrBlank()) name else "${scanlator}_$name"
        return DiskUtil.buildValidFilename(dirName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAscii) +
            "_" + md5(url).take(6)
    }

    private fun dirName0210(name: String, scanlator: String?, url: String, disallowNonAscii: Boolean): String {
        return DiskUtil.buildValidFilename(scanlator!!) + "_" +
            DiskUtil.buildValidFilename(name, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAscii) +
            "_" + md5(url).take(6)
    }

    @ParameterizedTest
    @MethodSource("chapters")
    fun `finds chapters downloaded before 0_21_0 and names new ones the same`(
        name: String,
        scanlator: String?,
        disallowNonAscii: Boolean,
    ) {
        val provider = provider(disallowNonAscii)
        val expected = dirNameBefore0210(name, scanlator, URL, disallowNonAscii)

        provider.getValidChapterDirNames(name, scanlator, URL) shouldContain expected
        provider.getChapterDirName(name, scanlator, URL) shouldBe expected
    }

    @ParameterizedTest
    @MethodSource("chapters")
    fun `finds chapters downloaded by 0_21_0 and 0_21_1`(name: String, scanlator: String?, disallowNonAscii: Boolean) {
        if (scanlator.isNullOrBlank()) return

        provider(disallowNonAscii).getValidChapterDirNames(name, scanlator, URL) shouldContain
            dirName0210(name, scanlator, URL, disallowNonAscii)
    }

    companion object {
        private const val URL = "/chapter/1"

        @JvmStatic
        fun chapters() = listOf(
            Arguments.of("Chapter 1", "Group", false),
            Arguments.of(" Chapter 1", "Group", false),
            Arguments.of(".Chapter 1", "Group", false),
            Arguments.of("Chapter 1", "Group ", false),
            Arguments.of("Chapter 1", "A/B ", false),
            Arguments.of("Chapter ".repeat(40), "Group", false),
            Arguments.of("Chapter 1", "翻訳グループ", true),
            Arguments.of(" Chapter 1", null, false),
        )
    }
}
