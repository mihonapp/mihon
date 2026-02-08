package tachiyomi.source.local

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.copyFromComicInfo
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalNovelSourceFileSystem
import tachiyomi.source.local.metadata.fillMetadata
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.days
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalNovelSource(
    private val context: Context,
    private val fileSystem: LocalNovelSourceFileSystem,
    private val coverManager: LocalCoverManager,
) : CatalogueSource, NovelSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    @Suppress("PrivatePropertyName")
    private val PopularFilters = FilterList(OrderBy.Popular(context))

    @Suppress("PrivatePropertyName")
    private val LatestFilters = FilterList(OrderBy.Latest(context))

    override val name: String = context.stringResource(MR.strings.local_novel_source)

    override val id: Long = ID

    override val lang: String = "other"

    override val isNovelSource: Boolean = true

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LatestFilters)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var novelDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Popular -> {
                    novelDirs = if (filter.state!!.ascending) {
                        novelDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        novelDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    }
                }
                is OrderBy.Latest -> {
                    novelDirs = if (filter.state!!.ascending) {
                        novelDirs.sortedBy(UniFile::lastModified)
                    } else {
                        novelDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        val novels = novelDirs
            .map { novelDir ->
                async {
                    SManga.create().apply {
                        title = novelDir.name.orEmpty()
                        url = novelDir.name.orEmpty()

                        // Try to find the cover
                        coverManager.find(novelDir.name.orEmpty())?.let {
                            thumbnail_url = it.uri.toString()
                        }
                    }
                }
            }
            .awaitAll()

        MangasPage(novels, false)
    }

    // Novel details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        coverManager.find(manga.url)?.let {
            manga.thumbnail_url = it.uri.toString()
        }

        // Augment novel details based on metadata files
        try {
            val novelDir = fileSystem.getNovelDirectory(manga.url) ?: error("${manga.url} is not a valid directory")
            val novelDirFiles = novelDir.listFiles().orEmpty()

            val comicInfoFile = novelDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val legacyJsonDetailsFile = novelDirFiles
                .firstOrNull { it.extension == "json" }

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    setNovelDetailsFromComicInfoFile(comicInfoFile.openInputStream(), manga)
                }

                // Old custom JSON format
                legacyJsonDetailsFile != null -> {
                    json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.openInputStream()).run {
                        title?.let { manga.title = it }
                        author?.let { manga.author = it }
                        artist?.let { manga.artist = it }
                        description?.let { manga.description = it }
                        genre?.let { manga.genre = it.joinToString() }
                        status?.let { manga.status = it }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error setting novel details from local metadata for ${manga.title}" }
        }

        return@withIOContext manga
    }

    private fun parseComicInfo(stream: InputStream): ComicInfo {
        return AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }
    }

    private fun setNovelDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        manga.copyFromComicInfo(parseComicInfo(stream))
    }

    private fun setChapterDetailsFromComicInfoFile(stream: InputStream, chapter: SChapter) {
        val comicInfo = parseComicInfo(stream)

        comicInfo.title?.let { chapter.name = it.value }
        comicInfo.number?.value?.toFloatOrNull()?.let { chapter.chapter_number = it }
        comicInfo.translator?.let { chapter.scanlator = it.value }
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> = withIOContext {
        val allChapters = mutableListOf<SChapter>()

        fileSystem.getFilesInNovelDirectory(manga.url)
            // Only keep supported formats
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { isChapterSupported(it) }
            .forEach { chapterFile ->
                // Check if this is a multi-chapter EPUB
                if (chapterFile.extension.equals("epub", true)) {
                    try {
                        chapterFile.epubReader(context).use { epub ->
                            // Try to find and set the cover image if not set yet
                            if (coverManager.find(manga.url) == null) {
                                try {
                                    val cover = epub.getCoverImage()
                                    if (cover != null) {
                                        epub.getInputStream(cover)?.use { stream ->
                                            coverManager.update(manga, stream)
                                        }
                                    }
                                } catch (e: Exception) {
                                    logcat(LogPriority.ERROR, e) { "Error extracting cover from ${chapterFile.name}" }
                                }
                            }

                            val tocChapters = epub.getTableOfContents()

                            // If EPUB has multiple TOC entries, create separate chapters
                            if (tocChapters.size > 1) {
                                tocChapters.forEach { tocEntry ->
                                    allChapters.add(
                                        SChapter.create().apply {
                                            // URL format: novelDir/epubFile.epub#chapterHref
                                            url = "${manga.url}/${chapterFile.name}#${tocEntry.href}"
                                            name = tocEntry.title
                                            date_upload = chapterFile.lastModified()
                                            chapter_number = ChapterRecognition
                                                .parseChapterNumber(
                                                    manga.title,
                                                    tocEntry.title,
                                                    tocEntry.order.toDouble(),
                                                )
                                                .toFloat()
                                        },
                                    )
                                }
                                return@forEach // Don't add the EPUB as a single chapter
                            }

                            // Single chapter EPUB - treat as before
                            allChapters.add(
                                SChapter.create().apply {
                                    url = "${manga.url}/${chapterFile.name}"
                                    name = chapterFile.nameWithoutExtension.orEmpty()
                                    date_upload = chapterFile.lastModified()
                                    chapter_number = ChapterRecognition
                                        .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                                        .toFloat()
                                    epub.fillMetadata(manga, this)
                                },
                            )
                        }
                    } catch (e: Throwable) {
                        logcat(LogPriority.ERROR, e) { "Error reading epub for ${chapterFile.name}" }
                        // Fallback: add as single chapter
                        allChapters.add(createSimpleChapter(manga, chapterFile))
                    }
                } else {
                    // Non-EPUB file/directory
                    allChapters.add(createSimpleChapter(manga, chapterFile))
                }
            }

        allChapters.sortedWith { c1, c2 ->
            c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
        }
    }

    private fun createSimpleChapter(manga: SManga, chapterFile: UniFile): SChapter {
        return SChapter.create().apply {
            url = "${manga.url}/${chapterFile.name}"
            name = if (chapterFile.isDirectory) {
                chapterFile.name
            } else {
                chapterFile.nameWithoutExtension
            }.orEmpty()
            date_upload = chapterFile.lastModified()
            chapter_number = ChapterRecognition
                .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                .toFloat()
        }
    }

    private fun isChapterSupported(file: UniFile): Boolean {
        if (file.isDirectory) return true
        val ext = file.extension?.lowercase() ?: return false
        return ext in SUPPORTED_EXTENSIONS
    }

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular(context))

    // Page list - for novels we return a single page with the chapter URL
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return listOf(Page(0, chapter.url))
    }

    // Novel source - fetch page text
    override suspend fun fetchPageText(page: Page): String = withIOContext {
        try {
            // Check if URL contains a chapter fragment (for multi-chapter EPUBs)
            // Format: novelDir/epubFile.epub#chapterHref
            val urlParts = page.url.split("#", limit = 2)
            val filePath = urlParts[0]
            val chapterFragment = urlParts.getOrNull(1)

            val (novelDirName, chapterName) = filePath.split('/', limit = 2)
            val chapterFile = fileSystem.getBaseDirectory()
                ?.findFile(novelDirName)
                ?.findFile(chapterName)
                ?: throw Exception(context.stringResource(MR.strings.chapter_not_found))

            when {
                chapterFile.isDirectory -> {
                    // Read all text/html files in the directory
                    val textFiles = chapterFile.listFiles()
                        ?.filter { isTextFile(it) }
                        ?.sortedBy { it.name }
                        ?: emptyList()

                    if (textFiles.isEmpty()) {
                        throw Exception("No text files found in chapter directory")
                    }

                    textFiles.joinToString("\n\n") { file ->
                        file.openInputStream().bufferedReader().readText()
                    }
                }
                chapterFile.extension.equals("epub", true) -> {
                    // Read epub content
                    chapterFile.epubReader(context).use { epub ->
                        if (chapterFragment != null) {
                            // Multi-chapter EPUB: read specific chapter
                            epub.getChapterContent(chapterFragment)
                        } else {
                            // Single chapter EPUB: read all content
                            epub.getTextContent()
                        }
                    }
                }
                isArchiveSupported(chapterFile) -> {
                    // Read text files from archive
                    chapterFile.archiveReader(context).use { reader ->
                        val textEntries = reader.useEntries { entries ->
                            entries.filter { it.isFile && isTextFileName(it.name) }
                                .sortedBy { it.name }
                                .toList()
                        }

                        textEntries.joinToString("\n\n") { entry ->
                            reader.getInputStream(entry.name)?.bufferedReader()?.readText() ?: ""
                        }
                    }
                }
                isTextFile(chapterFile) -> {
                    // Direct text file
                    chapterFile.openInputStream().bufferedReader().readText()
                }
                else -> {
                    throw Exception("Unsupported chapter format: ${chapterFile.extension}")
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error fetching page text for ${page.url}" }
            throw e
        }
    }

    private fun isTextFile(file: UniFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext in TEXT_EXTENSIONS
    }

    private fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private fun isArchiveSupported(file: UniFile): Boolean {
        return Archive.isSupported(file)
    }

    fun getFormat(chapter: SChapter): Format {
        try {
            val (novelDirName, chapterName) = chapter.url.split('/', limit = 2)
            return fileSystem.getBaseDirectory()
                ?.findFile(novelDirName)
                ?.findFile(chapterName)
                ?.let(Format.Companion::valueOf)
                ?: throw Exception(context.stringResource(MR.strings.chapter_not_found))
        } catch (e: Format.UnknownFormatException) {
            throw Exception(context.stringResource(MR.strings.local_invalid_format))
        } catch (e: Exception) {
            throw e
        }
    }

    companion object {
        const val ID = 1L // Different from LocalSource ID (0L)
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds

        private val SUPPORTED_EXTENSIONS = setOf(
            "txt", "text", // Plain text
            "html", "htm", "xhtml", // HTML
            "epub", // EPUB
            "zip", "cbz", // ZIP archives
            "rar", "cbr", // RAR archives
        )

        private val TEXT_EXTENSIONS = setOf(
            "txt",
            "text",
            "html",
            "htm",
            "xhtml",
        )
    }
}

fun Manga.isLocalNovel(): Boolean = source == LocalNovelSource.ID

fun DomainSource.isLocalNovel(): Boolean = id == LocalNovelSource.ID
