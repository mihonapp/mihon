package eu.kanade.tachiyomi.data.download

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.storage.service.StorageManager
import java.io.IOException

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

    @Test
    fun `hide lookup finds every compatible final and partial path without selecting unrelated chapters`() = runTest {
        val preferences = LibraryPreferences(InMemoryPreferenceStore())
        val storage = mockk<StorageManager>()
        val root = mockk<UniFile>()
        val sourceDir = mockk<UniFile>()
        val mangaDir = mockk<UniFile>()
        val source = mockk<Source>()
        val manga = Manga.create().copy(title = "Manga")
        val chapter = Chapter.create().copy(name = "Chapter 1", url = URL)
        val provider = DownloadProvider(mockk(relaxed = true), storage, preferences)
        every { storage.getDownloadsDirectory() } returns root
        every { root.findFile(any()) } returns sourceDir
        every { sourceDir.findFile(any()) } returns mangaDir
        listOf(root, sourceDir, mangaDir).forEach {
            every { it.exists() } returns true
            every { it.isDirectory } returns true
            every { it.canRead() } returns true
        }
        val names = provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url).distinct()
        val expected = listOf(names.first(), names.first() + "_tmp", names.first() + ".cbz_tmp", names.last())
            .distinct().associateWith { mockk<UniFile> { every { uri } returns mockk() } }
        val unrelated = mockk<UniFile>()
        every { mangaDir.findFile(any()) } answers {
            if (firstArg<String>() == "Chapter 2") unrelated else expected[firstArg<String>()]
        }

        provider.findChapterPathsForHide(listOf(chapter), listOf(chapter), manga, source)
            .shouldContainExactlyInAnyOrder(expected.values)
    }

    @Test
    fun `hide lookup preserves a sibling whose actual title is the selected partial name`() = runTest {
        val fixture = HideStorage()
        val chapter = Chapter.create().copy(id = 1, name = "Chapter 1", url = URL)
        val sibling = chapter.copy(id = 2, name = "Chapter 1_tmp", url = "/chapter/2")
        val selectedFinal = fixture.file("Chapter 1")
        fixture.file(sibling.name)

        fixture.provider.findChapterPathsForHide(
            listOf(chapter),
            listOf(chapter, sibling),
            fixture.manga,
            fixture.source,
        )
            .shouldContainExactlyInAnyOrder(listOf(selectedFinal))
    }

    @Test
    fun `hide lookup reports unavailable download root`() = runTest {
        val fixture = HideStorage()
        every { fixture.storage.getDownloadsDirectory() } returns null

        assertThrows<IOException> {
            fixture.provider.findChapterPathsForHide(emptyList(), emptyList(), fixture.manga, fixture.source)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "Chapter 1_tmp,false",
        "Chapter 1_tmp,true",
        "Chapter 1.cbz_tmp,false",
        "Chapter 1.cbz_tmp,true",
        "Chapter 1,false",
        "Chapter 1,true",
        "CHAPTER 1_TMP,false",
        "CHAPTER 1_TMP,true",
    )
    fun `hide lookup protects all final archive temporary and legacy claims of actual sibling chapters`(
        siblingTitle: String,
        hashEnabled: Boolean,
    ) = runTest {
        val fixture = HideStorage(hashEnabled)
        val chapter = Chapter.create().copy(id = 1, name = "Chapter 1", url = URL)
        val sibling = chapter.copy(id = 2, name = siblingTitle, url = "/chapter/2")
        val names = fixture.provider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
            .flatMap { listOf(it, it + "_tmp") }.distinct()
        val siblingNames = fixture.provider.getValidChapterDirNames(sibling.name, sibling.scanlator, sibling.url)
            .flatMap { listOf(it, it + "_tmp") }.toCollection(sortedSetOf(String.CASE_INSENSITIVE_ORDER))
        val files = names.associateWith(fixture::file)

        val found = fixture.provider.findChapterPathsForHide(
            listOf(chapter),
            listOf(chapter, sibling),
            fixture.manga,
            fixture.source,
        )

        found.shouldContainExactlyInAnyOrder(files.filterKeys { it !in siblingNames }.values)
        // There must still be an unambiguous URL-hashed download to remove.
        found shouldContain files.getValue(fixture.provider.getChapterDirName(chapter.name, null, URL, false, true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["root", "source", "manga"])
    fun `hide lookup reports unreadable existing storage directories`(level: String) = runTest {
        val fixture = HideStorage()
        every { fixture.directoryAt(level).canRead() } returns false

        assertThrows<IOException> {
            fixture.provider.findChapterPathsForHide(emptyList(), emptyList(), fixture.manga, fixture.source)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["root", "source", "manga"])
    fun `hide lookup reports missing directory handles as inaccessible storage`(level: String) = runTest {
        val fixture = HideStorage()
        every { fixture.directoryAt(level).exists() } returns false

        assertThrows<IOException> {
            fixture.provider.findChapterPathsForHide(emptyList(), emptyList(), fixture.manga, fixture.source)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["source", "manga", "chapter"])
    fun `hide lookup succeeds when download is absent under readable storage`(level: String) = runTest {
        val fixture = HideStorage()
        when (level) {
            "source" -> every { fixture.root.findFile(any()) } returns null
            "manga" -> every { fixture.sourceDir.findFile(any()) } returns null
        }
        val chapter = Chapter.create().copy(id = 1, name = "Chapter 1", url = URL)

        fixture.provider.findChapterPathsForHide(listOf(chapter), listOf(chapter), fixture.manga, fixture.source)
            .shouldContainExactlyInAnyOrder(emptyList())
    }

    @Test
    fun `hide lookup reports storage lost during child lookup`() = runTest {
        val fixture = HideStorage()
        every { fixture.root.exists() } returnsMany listOf(true, false)
        every { fixture.root.findFile(any()) } returns null

        assertThrows<IOException> {
            fixture.provider.findChapterPathsForHide(emptyList(), emptyList(), fixture.manga, fixture.source)
        }
    }

    private class HideStorage(hashEnabled: Boolean = false) {
        val storage = mockk<StorageManager>()
        val root = directory()
        val sourceDir = directory()
        val mangaDir = directory()
        val source = mockk<Source>()
        val manga = Manga.create().copy(title = "Manga")
        val provider = DownloadProvider(
            mockk(relaxed = true),
            storage,
            LibraryPreferences(InMemoryPreferenceStore()).apply { enableChapterNameHash.set(hashEnabled) },
        )
        private val files = mutableMapOf<String, UniFile>()

        init {
            every { storage.getDownloadsDirectory() } returns root
            every { root.findFile(any()) } returns sourceDir
            every { sourceDir.findFile(any()) } returns mangaDir
            every { mangaDir.findFile(any()) } answers { files[firstArg<String>()] }
        }

        fun directoryAt(level: String): UniFile = when (level) {
            "root" -> root
            "source" -> sourceDir
            "manga" -> mangaDir
            else -> error("Unknown directory level")
        }

        fun file(name: String): UniFile = mockk<UniFile> {
            every { uri } returns mockk()
        }.also { files[name] = it }

        private fun directory() = mockk<UniFile> {
            every { exists() } returns true
            every { isDirectory } returns true
            every { canRead() } returns true
        }
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
