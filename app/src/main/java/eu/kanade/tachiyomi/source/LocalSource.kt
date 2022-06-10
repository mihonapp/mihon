package eu.kanade.tachiyomi.source

import android.content.Context
import com.github.junrar.Archive
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class LocalSource(
    private val context: Context,
    private val coverCache: CoverCache = Injekt.get(),
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()

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

                else -> { /* Do nothing */ }
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
            val mangaInfo = manga.toMangaInfo()
            runBlocking {
                val chapters = getChapterList(mangaInfo)
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last().toSChapter()
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
    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        var mangaInfo = manga

        val baseDirsFile = getBaseDirectoriesFiles(context)

        val coverFile = getCoverFile(manga.key, baseDirsFile)

        coverFile?.let {
            mangaInfo = mangaInfo.copy(cover = it.absolutePath)
        }

        val localDetails = getMangaDirsFiles(manga.key, baseDirsFile)
            .firstOrNull { it.extension.equals("json", ignoreCase = true) }

        if (localDetails != null) {
            val obj = json.decodeFromStream<JsonObject>(localDetails.inputStream())

            mangaInfo = mangaInfo.copy(
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: mangaInfo.title,
                author = obj["author"]?.jsonPrimitive?.contentOrNull ?: mangaInfo.author,
                artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: mangaInfo.artist,
                description = obj["description"]?.jsonPrimitive?.contentOrNull ?: mangaInfo.description,
                genres = obj["genre"]?.jsonArray?.map { it.jsonPrimitive.content } ?: mangaInfo.genres,
                status = obj["status"]?.jsonPrimitive?.intOrNull ?: mangaInfo.status,
            )
        }

        return mangaInfo
    }

    // Chapters
    override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
        val sManga = manga.toSManga()

        val baseDirsFile = getBaseDirectoriesFiles(context)
        return getMangaDirsFiles(manga.key, baseDirsFile)
            // Only keep supported formats
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

                    chapter_number = ChapterRecognition.parseChapterNumber(sManga.title, this.name, this.chapter_number)

                    val format = getFormat(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }
                }
            }
            .map { it.toChapterInfo() }
            .sortedWith { c1, c2 ->
                val c = c2.number.compareTo(c1.number)
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
    override suspend fun getPageList(chapter: ChapterInfo) = throw UnsupportedOperationException("Unused")

    // Miscellaneous
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
            }

            // It might not exist at this point
            coverFile.parentFile?.mkdirs()
            inputStream.use { input ->
                coverFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Create a .nomedia file
            DiskUtil.createNoMediaFile(UniFile.fromFile(mangaDir), context)

            manga.thumbnail_url = coverFile.absolutePath
            return coverFile
        }
    }
}

private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "epub")
