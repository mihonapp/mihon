package eu.kanade.tachiyomi.source

import android.content.Context
import com.github.junrar.Archive
import com.hippo.unifile.UniFile
import eu.kanade.domain.manga.model.COMIC_INFO_FILE
import eu.kanade.domain.manga.model.ComicInfo
import eu.kanade.domain.manga.model.copyFromComicInfo
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.EpubFile
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LocalSource(
    private val context: Context,
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    override val name: String = context.getString(R.string.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val baseDirsFiles = getBaseDirectoriesFiles(context)

        var mangaDirs = baseDirsFiles
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .distinctBy { it.name }

        val lastModifiedLimit = if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        // Filter by query or last modified
        mangaDirs = mangaDirs.filter {
            if (lastModifiedLimit == 0L) {
                it.name.contains(query, ignoreCase = true)
            } else {
                it.lastModified() >= lastModifiedLimit
            }
        }

        filters.forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    when (filter.state!!.index) {
                        0 -> {
                            mangaDirs = if (filter.state!!.ascending) {
                                mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                            } else {
                                mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                            }
                        }
                        1 -> {
                            mangaDirs = if (filter.state!!.ascending) {
                                mangaDirs.sortedBy(File::lastModified)
                            } else {
                                mangaDirs.sortedByDescending(File::lastModified)
                            }
                        }
                    }
                }

                else -> {
                    /* Do nothing */
                }
            }
        }

        // Transform mangaDirs to list of SManga
        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                val cover = getCoverFile(mangaDir.name, baseDirsFiles)
                if (cover != null && cover.exists()) {
                    thumbnail_url = cover.absolutePath
                }
            }
        }

        // Fetch chapters of all the manga
        mangas.forEach { manga ->
            runBlocking {
                val chapters = getChapterList(manga)
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last()
                    val format = getFormat(chapter)

                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillMangaMetadata(manga)
                        }
                    }

                    // Copy the cover from the first chapter found if not available
                    if (manga.thumbnail_url == null) {
                        updateCover(chapter, manga)
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        val baseDirsFile = getBaseDirectoriesFiles(context)

        getCoverFile(manga.url, baseDirsFile)?.let {
            manga.thumbnail_url = it.absolutePath
        }

        // Augment manga details based on metadata files
        try {
            val mangaDirFiles = getMangaDirsFiles(manga.url, baseDirsFile).toList()
            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val noXmlFile = mangaDirFiles
                .firstOrNull { it.name == ".noxml" }
            if (comicInfoFile != null && noXmlFile != null) noXmlFile.delete()

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    setMangaDetailsFromComicInfoFile(comicInfoFile.inputStream(), manga)
                }

                // Copy ComicInfo.xml from chapter archive to top level if found
                noXmlFile == null -> {
                    val chapterArchives = mangaDirFiles
                        .filter { isSupportedArchiveFile(it.extension) }
                        .toList()

                    val mangaDir = getMangaDir(manga.url, baseDirsFile)
                    val folderPath = mangaDir?.absolutePath

                    val copiedFile = copyComicInfoFileFromArchive(chapterArchives, folderPath)
                    if (copiedFile != null) {
                        setMangaDetailsFromComicInfoFile(copiedFile.inputStream(), manga)
                    } else {
                        // Avoid re-scanning
                        File("$folderPath/.noxml").createNewFile()
                    }
                }

                // Fall back to legacy JSON details format
                else -> {
                    mangaDirFiles
                        .firstOrNull { it.extension == "json" }
                        ?.let { file ->
                            json.decodeFromStream<MangaDetails>(file.inputStream()).run {
                                title?.let { manga.title = it }
                                author?.let { manga.author = it }
                                artist?.let { manga.artist = it }
                                description?.let { manga.description = it }
                                genre?.let { manga.genre = it.joinToString() }
                                status?.let { manga.status = it }
                            }
                        }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error setting manga details from local metadata for ${manga.title}" }
        }

        return@withIOContext manga
    }

    private fun copyComicInfoFileFromArchive(chapterArchives: List<File>, folderPath: String?): File? {
        for (chapter in chapterArchives) {
            when (getFormat(chapter)) {
                is Format.Zip -> {
                    ZipFile(chapter).use { zip: ZipFile ->
                        zip.getEntry(COMIC_INFO_FILE)?.let { comicInfoFile ->
                            zip.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                is Format.Rar -> {
                    Archive(chapter).use { rar: Archive ->
                        rar.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }?.let { comicInfoFile ->
                            rar.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    private fun copyComicInfoFile(comicInfoFileStream: InputStream, folderPath: String?): File {
        return File("$folderPath/$COMIC_INFO_FILE").apply {
            outputStream().use { outputStream ->
                comicInfoFileStream.use { it.copyTo(outputStream) }
            }
        }
    }

    private fun setMangaDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        val comicInfo = AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }

        manga.copyFromComicInfo(comicInfo)
    }

    @Serializable
    class MangaDetails(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Int? = null,
    )

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val baseDirsFile = getBaseDirectoriesFiles(context)
        return getMangaDirsFiles(manga.url, baseDirsFile)
            // Only keep supported formats
            .filter { it.isDirectory || isSupportedArchiveFile(it.extension) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()
                    chapter_number = ChapterRecognition.parseChapterNumber(manga.title, this.name, this.chapter_number)

                    val format = getFormat(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()
    }

    // Filters
    override fun getFilterList() = FilterList(OrderBy(context))

    private val POPULAR_FILTERS = FilterList(OrderBy(context))
    private val LATEST_FILTERS = FilterList(OrderBy(context).apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : Filter.Sort(
        context.getString(R.string.local_filter_order_by),
        arrayOf(context.getString(R.string.title), context.getString(R.string.date)),
        Selection(0, true),
    )

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException("Unused")

    // Miscellaneous
    private fun isSupportedArchiveFile(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }

    fun getFormat(chapter: SChapter): Format {
        val baseDirs = getBaseDirectories(context)

        for (dir in baseDirs) {
            val chapFile = File(dir, chapter.url)
            if (!chapFile.exists()) continue

            return getFormat(chapFile)
        }
        throw Exception(context.getString(R.string.chapter_not_found))
    }

    private fun getFormat(file: File) = with(file) {
        when {
            isDirectory -> Format.Directory(this)
            extension.equals("zip", true) || extension.equals("cbz", true) -> Format.Zip(this)
            extension.equals("rar", true) || extension.equals("cbr", true) -> Format.Rar(this)
            extension.equals("epub", true) -> Format.Epub(this)
            else -> throw Exception(context.getString(R.string.local_invalid_format))
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                    entry?.let { updateCover(context, manga, it.inputStream()) }
                }
                is Format.Zip -> {
                    ZipFile(format.file).use { zip ->
                        val entry = zip.entries().toList()
                            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                        entry?.let { updateCover(context, manga, zip.getInputStream(it)) }
                    }
                }
                is Format.Rar -> {
                    Archive(format.file).use { archive ->
                        val entry = archive.fileHeaders
                            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                        entry?.let { updateCover(context, manga, archive.getInputStream(it)) }
                    }
                }
                is Format.Epub -> {
                    EpubFile(format.file).use { epub ->
                        val entry = epub.getImagesFromPages()
                            .firstOrNull()
                            ?.let { epub.getEntry(it) }

                        entry?.let { updateCover(context, manga, epub.getInputStream(it)) }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        private const val DEFAULT_COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        private fun getBaseDirectories(context: Context): Sequence<File> {
            val localFolder = context.getString(R.string.app_name) + File.separator + "local"
            return DiskUtil.getExternalStorages(context)
                .map { File(it.absolutePath, localFolder) }
                .asSequence()
        }

        private fun getBaseDirectoriesFiles(context: Context): Sequence<File> {
            return getBaseDirectories(context)
                // Get all the files inside all baseDir
                .flatMap { it.listFiles().orEmpty().toList() }
        }

        private fun getMangaDir(mangaUrl: String, baseDirsFile: Sequence<File>): File? {
            return baseDirsFile
                // Get the first mangaDir or null
                .firstOrNull { it.isDirectory && it.name == mangaUrl }
        }

        private fun getMangaDirsFiles(mangaUrl: String, baseDirsFile: Sequence<File>): Sequence<File> {
            return baseDirsFile
                // Filter out ones that are not related to the manga and is not a directory
                .filter { it.isDirectory && it.name == mangaUrl }
                // Get all the files inside the filtered folders
                .flatMap { it.listFiles().orEmpty().toList() }
        }

        private fun getCoverFile(mangaUrl: String, baseDirsFile: Sequence<File>): File? {
            return getMangaDirsFiles(mangaUrl, baseDirsFile)
                // Get all file whose names start with 'cover'
                .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
                // Get the first actual image
                .firstOrNull {
                    ImageUtil.isImage(it.name) { it.inputStream() }
                }
        }

        fun updateCover(context: Context, manga: SManga, inputStream: InputStream): File? {
            val baseDirsFiles = getBaseDirectoriesFiles(context)

            val mangaDir = getMangaDir(manga.url, baseDirsFiles)
            if (mangaDir == null) {
                inputStream.close()
                return null
            }

            var coverFile = getCoverFile(manga.url, baseDirsFiles)
            if (coverFile == null) {
                coverFile = File(mangaDir.absolutePath, DEFAULT_COVER_NAME)
                coverFile.createNewFile()
            }

            // It might not exist at this point
            coverFile.parentFile?.mkdirs()
            inputStream.use { input ->
                coverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            DiskUtil.createNoMediaFile(UniFile.fromFile(mangaDir), context)

            manga.thumbnail_url = coverFile.absolutePath
            return coverFile
        }
    }
}

private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")
