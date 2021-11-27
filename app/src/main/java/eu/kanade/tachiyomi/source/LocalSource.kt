package eu.kanade.tachiyomi.source

import android.content.Context
import com.github.junrar.Archive
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toChapterInfo
import eu.kanade.tachiyomi.source.model.toMangaInfo
import eu.kanade.tachiyomi.source.model.toSChapter
import eu.kanade.tachiyomi.source.model.toSManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.EpubFile
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LocalSource(private val context: Context) : CatalogueSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        private const val COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        fun updateCover(context: Context, manga: SManga, input: InputStream): File? {
            val dir = getBaseDirectories(context).firstOrNull()
            if (dir == null) {
                input.close()
                return null
            }
            var cover = getCoverFile(File("${dir.absolutePath}/${manga.url}"))
            if (cover == null) {
                cover = File("${dir.absolutePath}/${manga.url}", COVER_NAME)
            }
            // It might not exist if using the external SD card
            cover.parentFile?.mkdirs()
            input.use {
                cover.outputStream().use {
                    input.copyTo(it)
                }
            }
            manga.thumbnail_url = cover.absolutePath
            return cover
        }

        /**
         * Returns valid cover file inside [parent] directory.
         */
        private fun getCoverFile(parent: File): File? {
            return parent.listFiles()?.find { it.nameWithoutExtension == "cover" }?.takeIf {
                it.isFile && ImageUtil.isImage(it.name) { it.inputStream() }
            }
        }

        private fun getBaseDirectories(context: Context): List<File> {
            val c = context.getString(R.string.app_name) + File.separator + "local"
            return DiskUtil.getExternalStorages(context).map { File(it.absolutePath, c) }
        }
    }

    private val json: Json by injectLazy()

    override val id = ID
    override val name = context.getString(R.string.local_source)
    override val lang = "other"
    override val supportsLatest = true

    override fun toString() = name

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val baseDirs = getBaseDirectories(context)

        val time = if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        var mangaDirs = baseDirs
            .asSequence()
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory }
            .filterNot { it.name.startsWith('.') }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) POPULAR_FILTERS else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy { it.name.lowercase(Locale.ENGLISH) }
                } else {
                    mangaDirs.sortedByDescending { it.name.lowercase(Locale.ENGLISH) }
                }
            }
            1 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy(File::lastModified)
                } else {
                    mangaDirs.sortedByDescending(File::lastModified)
                }
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                for (dir in baseDirs) {
                    val cover = getCoverFile(File("${dir.absolutePath}/$url"))
                    if (cover != null && cover.exists()) {
                        thumbnail_url = cover.absolutePath
                        break
                    }
                }

                val sManga = this
                val mangaInfo = this.toMangaInfo()
                runBlocking {
                    val chapters = getChapterList(mangaInfo)
                    if (chapters.isNotEmpty()) {
                        val chapter = chapters.last().toSChapter()
                        val format = getFormat(chapter)
                        if (format is Format.Epub) {
                            EpubFile(format.file).use { epub ->
                                epub.fillMangaMetadata(sManga)
                            }
                        }

                        // Copy the cover from the first chapter found.
                        if (thumbnail_url == null) {
                            try {
                                val dest = updateCover(chapter, sManga)
                                thumbnail_url = dest?.absolutePath
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e)
                            }
                        }
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        val localDetails = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.key).listFiles()?.toList() }
            .flatten()
            .firstOrNull { it.extension.lowercase() == "json" }

        return if (localDetails != null) {
            val obj = json.decodeFromStream<JsonObject>(localDetails.inputStream())

            manga.copy(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: manga.title,
                author = obj["author"]?.jsonPrimitive?.contentOrNull ?: manga.author,
                artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: manga.artist,
                description = obj["description"]?.jsonPrimitive?.contentOrNull ?: manga.description,
                genres = obj["genre"]?.jsonArray?.map { it.jsonPrimitive.content } ?: manga.genres,
                status = obj["status"]?.jsonPrimitive?.intOrNull ?: manga.status,
            )
        } else {
            manga
        }
    }

    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val sManga = manga.toSManga()

        val chapters = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, manga.key).listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.key}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()

                    val format = getFormat(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }

                    name = getCleanChapterTitle(name, manga.title)
                    ChapterRecognition.parseChapterNumber(this, sManga)
                }
            }
            .map { it.toChapterInfo() }
            .sortedWith { c1, c2 ->
                val c = c2.number.compareTo(c1.number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()

        return chapters
    }

    override suspend fun getPageList(chapter: ChapterInfo) = throw Exception("Unused")

    /**
     * Strips the manga title from a chapter name and trim whitespace/delimiter characters.
     */
    private fun getCleanChapterTitle(chapterName: String, mangaTitle: String): String {
        return chapterName
            .replace(mangaTitle, "")
            .trim(*WHITESPACE_CHARS.toCharArray(), '-', '_', ',', ':')
    }

    private fun isSupportedFile(extension: String): Boolean {
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
        return when (val format = getFormat(chapter)) {
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
    }

    override fun getFilterList() = POPULAR_FILTERS

    private val POPULAR_FILTERS = FilterList(OrderBy(context))
    private val LATEST_FILTERS = FilterList(OrderBy(context).apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : Filter.Sort(
        context.getString(R.string.local_filter_order_by),
        arrayOf(context.getString(R.string.title), context.getString(R.string.date)),
        Selection(0, true)
    )

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }
}

private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")

private val WHITESPACE_CHARS = arrayOf(
    ' ',
    '\u0009',
    '\u000A',
    '\u000B',
    '\u000C',
    '\u000D',
    '\u0020',
    '\u0085',
    '\u00A0',
    '\u1680',
    '\u2000',
    '\u2001',
    '\u2002',
    '\u2003',
    '\u2004',
    '\u2005',
    '\u2006',
    '\u2007',
    '\u2008',
    '\u2009',
    '\u200A',
    '\u2028',
    '\u2029',
    '\u202F',
    '\u205F',
    '\u3000',
)
