package tachiyomi.source.local

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.EpubFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import mihon.core.common.extensions.toZipFile
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.storage.openReadOnlyChannel
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.core.metadata.comicinfo.ComicInfoPublishingStatus
import tachiyomi.core.metadata.comicinfo.copyFromComicInfo
import tachiyomi.core.metadata.comicinfo.getComicInfo
import tachiyomi.core.metadata.tachiyomi.MangaDetails
import tachiyomi.domain.chapter.service.ChapterRecognition
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.ArtistFilter
import tachiyomi.source.local.filter.ArtistGroup
import tachiyomi.source.local.filter.ArtistTextSearch
import tachiyomi.source.local.filter.AuthorFilter
import tachiyomi.source.local.filter.AuthorGroup
import tachiyomi.source.local.filter.AuthorTextSearch
import tachiyomi.source.local.filter.GenreFilter
import tachiyomi.source.local.filter.GenreGroup
import tachiyomi.source.local.filter.GenreTextSearch
import tachiyomi.source.local.filter.LocalSourceInfoHeader
import tachiyomi.source.local.filter.OrderBy
import tachiyomi.source.local.filter.Separator
import tachiyomi.source.local.filter.StatusFilter
import tachiyomi.source.local.filter.StatusGroup
import tachiyomi.source.local.filter.TextSearchHeader
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.Format
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.metadata.fillMetadata
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.github.junrar.Archive as JunrarArchive
import tachiyomi.domain.source.model.Source as DomainSource

actual class LocalSource(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
) : CatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()
    private val mangaRepository: MangaRepository by injectLazy()

    private var mangaChunks: MutableList<List<SManga>> = mutableListOf()
    private var mangaDirChunks: List<List<UniFile>> =
        fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .asSequence()
            .filter { it.isDirectory && it.name?.startsWith('.') == false }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
            .chunked(CHUNK_SIZE)
            .toList()

    private var loadedPages = 0
    private var currentlyLoadingPage: Int? = null
    private var includedChunkIndex = -1
    private var allMangaLoaded = false
    private var isFilteredSearch = false

    private val POPULAR_FILTERS = FilterList(OrderBy.Popular(context))
    private val LATEST_FILTERS = FilterList(OrderBy.Latest(context))

    override val name: String = context.stringResource(MR.strings.local_source)

    override val id: Long = ID

    override val lang: String = "other"

    override fun toString() = name

    override val supportsLatest: Boolean = true

    private fun checkForNewManga() {
        if (!allMangaLoaded || currentlyLoadingPage != null) return

        val newManga = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .asSequence()
            .filter { it.isDirectory && it.name?.startsWith('.') == false }
            .filterNot { mangaDir -> mangaDirChunks.flatten().map { it }.contains(mangaDir) }
            .distinctBy { it.name }
            .sortedBy { it.lastModified() }
            .toList()

        if (newManga.isNotEmpty()) {
            mangaDirChunks = mangaDirChunks
                .flatten()
                .plus(newManga)
                .distinctBy { it.name }
                .chunked(CHUNK_SIZE)

            allMangaLoaded = false
            if (mangaChunks.last().size % CHUNK_SIZE != 0) {
                mangaChunks = mangaChunks.dropLast(1).toMutableList()
                loadedPages--
            }
        }
    }

    private fun loadMangaForPage(page: Int) {
        if (page != loadedPages + 1 || page == currentlyLoadingPage) return
        currentlyLoadingPage = loadedPages + 1

        val localMangaList = runBlocking { getMangaList() }
        val mangaPage = mangaDirChunks[page - 1].map { mangaDir ->
            SManga.create().apply manga@{
                url = mangaDir.name.toString()
                dirLastModifiedAt = mangaDir.lastModified()

                mangaDir.name?.let { title = localMangaList[url]?.title ?: it }
                author = localMangaList[url]?.author
                artist = localMangaList[url]?.artist
                description = localMangaList[url]?.description
                genre = localMangaList[url]?.genre?.joinToString(", ") { it.trim() }
                status = localMangaList[url]?.status?.toInt() ?: ComicInfoPublishingStatus.toSMangaValue("Unknown")

                // Try to find the cover
                coverManager.find(mangaDir.name.orEmpty())?.let {
                    thumbnail_url = it.uri.toString()
                }

                // Fetch chapters and fill metadata
                runBlocking {
                    val chapters = getChapterList(this@manga)
                    if (chapters.isNotEmpty()) {
                        val chapter = chapters.last()

                        // only read metadata from disk if it the mangaDir has been modified
                        if (dirLastModifiedAt != localMangaList[url]?.dirLastModifiedAt) {
                            when (val format = getFormat(chapter)) {
                                is Format.Directory -> getMangaDetails(this@manga)
                                is Format.Zip -> getMangaDetails(this@manga)
                                is Format.Rar -> getMangaDetails(this@manga)
                                is Format.Epub -> EpubFile(format.file.openReadOnlyChannel(context)).use { epub ->
                                    epub.fillMetadata(this@manga, chapter)
                                }
                            }
                        }
                        // Copy the cover from the first chapter found if not available
                        if (this@manga.thumbnail_url == null) {
                            updateCover(chapter, this@manga)
                        }
                    }
                }
            }
        }.toList()

        mangaChunks.add(mangaPage)
        loadedPages++
        currentlyLoadingPage = null
    }

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", POPULAR_FILTERS)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LATEST_FILTERS)

    enum class OrderByPopular {
        NOT_SET,
        POPULAR_ASCENDING,
        POPULAR_DESCENDING,
    }
    enum class OrderByLatest {
        NOT_SET,
        LATEST,
        OLDEST,
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withIOContext {
        if (page == 1) checkForNewManga()
        loadMangaForPage(page)

        while (page == currentlyLoadingPage) {
            runBlocking { delay(200) }
        }

        var includedManga: MutableList<SManga>

        var orderByPopular =
            if (filters === POPULAR_FILTERS) {
                OrderByPopular.POPULAR_ASCENDING
            } else {
                OrderByLatest.NOT_SET
            }
        var orderByLatest =
            if (filters === LATEST_FILTERS) {
                OrderByLatest.LATEST
            } else {
                OrderByLatest.NOT_SET
            }

        val includedGenres = mutableListOf<String>()
        val includedAuthors = mutableListOf<String>()
        val includedArtists = mutableListOf<String>()
        val includedStatuses = mutableListOf<String>()

        val excludedGenres = mutableListOf<String>()
        val excludedAuthors = mutableListOf<String>()
        val excludedArtists = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is OrderBy.Popular -> {
                    orderByPopular = if (filter.state!!.ascending) {
                        OrderByPopular.POPULAR_ASCENDING
                    } else {
                        OrderByPopular.POPULAR_DESCENDING
                    }
                }
                is OrderBy.Latest -> {
                    orderByLatest = if (filter.state!!.ascending) {
                        OrderByLatest.LATEST
                    } else {
                        OrderByLatest.OLDEST
                    }
                }

                // included Filter
                is GenreGroup -> {
                    filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE -> {
                                includedGenres.add(genre.name)
                            }
                        }
                    }
                }
                is GenreTextSearch -> {
                    val genreList = filter.state.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
                    genreList?.forEach {
                        when (it.first()) {
                            '-' -> excludedGenres.add(it.drop(1).trim())
                            else -> includedGenres.add(it)
                        }
                    }
                }
                is AuthorGroup -> {
                    filter.state.forEach { author ->
                        when (author.state) {
                            Filter.TriState.STATE_INCLUDE -> {
                                includedAuthors.add(author.name)
                            }
                        }
                    }
                }
                is AuthorTextSearch -> {
                    val authorList = filter.state.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
                    authorList?.forEach {
                        when (it.first()) {
                            '-' -> excludedAuthors.add(it.drop(1).trim())
                            else -> includedAuthors.add(it)
                        }
                    }
                }
                is ArtistGroup -> {
                    filter.state.forEach { artist ->
                        when (artist.state) {
                            Filter.TriState.STATE_INCLUDE -> {
                                includedArtists.add(artist.name)
                            }
                        }
                    }
                }
                is ArtistTextSearch -> {
                    val artistList = filter.state.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }
                    artistList?.forEach {
                        when (it.first()) {
                            '-' -> excludedArtists.add(it.drop(1).trim())
                            else -> includedArtists.add(it)
                        }
                    }
                }
                is StatusGroup -> {
                    filter.state.forEach { status ->
                        when (status.state) {
                            Filter.TriState.STATE_INCLUDE -> {
                                includedStatuses.add(status.name)
                            }
                        }
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        includedManga = mangaChunks.flatten().filter { manga ->
            (manga.title.contains(query, ignoreCase = true) || File(manga.url).name.contains(query, ignoreCase = true)) &&
                areAllElementsInMangaEntry(includedGenres, manga.genre) &&
                areAllElementsInMangaEntry(includedAuthors, manga.author) &&
                areAllElementsInMangaEntry(includedArtists, manga.artist) &&
                (if (includedStatuses.isNotEmpty()) includedStatuses.map { ComicInfoPublishingStatus.toSMangaValue(it) }.contains(manga.status) else true)
        }.toMutableList()

        if (query.isBlank() &&
            includedGenres.isEmpty() &&
            includedAuthors.isEmpty() &&
            includedArtists.isEmpty() &&
            includedStatuses.isEmpty()
        ) {
            includedManga = mangaChunks.flatten().toMutableList()
            isFilteredSearch = false
        } else {
            isFilteredSearch = true
        }

        filters.forEach { filter ->
            when (filter) {
                // excluded Filter
                is GenreGroup -> {
                    filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_EXCLUDE -> {
                                excludedGenres.add(genre.name)
                            }
                        }
                    }
                }
                is AuthorGroup -> {
                    filter.state.forEach { author ->
                        when (author.state) {
                            Filter.TriState.STATE_EXCLUDE -> {
                                excludedAuthors.add(author.name)
                            }
                        }
                    }
                }
                is ArtistGroup -> {
                    filter.state.forEach { artist ->
                        when (artist.state) {
                            Filter.TriState.STATE_EXCLUDE -> {
                                excludedArtists.add(artist.name)
                            }
                        }
                    }
                }
                is StatusGroup -> {
                    filter.state.forEach { status ->
                        when (status.state) {
                            Filter.TriState.STATE_EXCLUDE -> {
                                isFilteredSearch = true
                                includedManga.removeIf { manga ->
                                    ComicInfoPublishingStatus.toComicInfoValue(manga.status.toLong()) == status.name
                                }
                            }
                        }
                    }
                }

                else -> {
                    /* Do nothing */
                }
            }
        }
        excludedGenres.forEach { genre ->
            isFilteredSearch = true
            includedManga.removeIf { manga ->
                manga.genre?.split(",")?.map { it.trim() }?.any { it.equals(genre, ignoreCase = true) } ?: false
            }
        }
        excludedAuthors.forEach { author ->
            isFilteredSearch = true
            includedManga.removeIf { manga ->
                manga.author?.split(",")?.map { it.trim() }?.any { it.equals(author, ignoreCase = true) } ?: false
            }
        }
        excludedArtists.forEach { artist ->
            isFilteredSearch = true
            includedManga.removeIf { manga ->
                manga.artist?.split(",")?.map { it.trim() }?.any { it.equals(artist, ignoreCase = true) } ?: false
            }
        }

        when (orderByPopular) {
            OrderByPopular.POPULAR_ASCENDING ->
                includedManga = if (allMangaLoaded || isFilteredSearch) {
                    includedManga.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toMutableList()
                } else {
                    includedManga
                }

            OrderByPopular.POPULAR_DESCENDING ->
                includedManga = if (allMangaLoaded || isFilteredSearch) {
                    includedManga.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toMutableList()
                } else {
                    includedManga
                }

            OrderByPopular.NOT_SET -> Unit
        }
        when (orderByLatest) {
            OrderByLatest.LATEST ->
                includedManga = if (allMangaLoaded || isFilteredSearch) {
                    includedManga.sortedBy { it.dirLastModifiedAt }
                        .toMutableList()
                } else {
                    includedManga
                }

            OrderByLatest.OLDEST ->
                includedManga = if (allMangaLoaded || isFilteredSearch) {
                    includedManga.sortedByDescending { it.dirLastModifiedAt }
                        .toMutableList()
                } else {
                    includedManga
                }

            OrderByLatest.NOT_SET -> Unit
        }

        val mangaPageList =
            if (includedManga.isNotEmpty()) {
                includedManga.toList().chunked(CHUNK_SIZE)
            } else {
                listOf(emptyList())
            }

        if (page == 1) includedChunkIndex = -1
        if (includedChunkIndex < mangaPageList.lastIndex) {
            includedChunkIndex++
        } else {
            includedChunkIndex = mangaPageList.lastIndex
        }

        val lastLocalMangaPageReached = (mangaDirChunks.lastIndex == page - 1)
        if (lastLocalMangaPageReached) allMangaLoaded = true

        val lastPage = (lastLocalMangaPageReached || (isFilteredSearch && includedChunkIndex == mangaPageList.lastIndex))

        MangasPage(mangaPageList[includedChunkIndex], !lastPage)
    }

    private fun areAllElementsInMangaEntry(includedList: MutableList<String>, mangaEntry: String?): Boolean {
        return if (includedList.isNotEmpty()) {
            mangaEntry?.split(",")?.map { it.trim() }
                ?.let { mangaEntryList ->
                    includedList.all { includedEntry ->
                        mangaEntryList.any { mangaEntry ->
                            mangaEntry.equals(includedEntry, ignoreCase = true)
                        }
                    }
                } ?: false
        } else {
            true
        }
    }

    private suspend fun getMangaList(): Map<String?, Manga?> {
        return fileSystem.getFilesInBaseDirectory().toList()
            .filter { it.isDirectory && it.name?.startsWith('.') == false }
            .map { file ->
                file.name?.let { mangaRepository.getMangaByUrlAndSourceId(it, ID) }
            }
            .associateBy { it?.url }
    }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        coverManager.find(manga.url)?.let {
            manga.thumbnail_url = it.uri.toString()
        }

        // Augment manga details based on metadata files
        try {
            val mangaDir = fileSystem.getMangaDirectory(manga.url) ?: error("${manga.url} is not a valid directory")
            val mangaDirFiles = mangaDir.listFiles().orEmpty()

            val comicInfoFile = mangaDirFiles
                .firstOrNull { it.name == COMIC_INFO_FILE }
            val noXmlFile = mangaDirFiles
                .firstOrNull { it.name == ".noxml" }
            val legacyJsonDetailsFile = mangaDirFiles
                .firstOrNull { it.extension == "json" }

            when {
                // Top level ComicInfo.xml
                comicInfoFile != null -> {
                    noXmlFile?.delete()
                    setMangaDetailsFromComicInfoFile(comicInfoFile.openInputStream(), manga)
                }

                // Old custom JSON format
                // TODO: remove support for this entirely after a while
                legacyJsonDetailsFile != null -> {
                    json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.openInputStream()).run {
                        title?.let { manga.title = it }
                        author?.let { manga.author = it }
                        artist?.let { manga.artist = it }
                        description?.let { manga.description = it }
                        genre?.let { manga.genre = it.joinToString() }
                        status?.let { manga.status = it }
                    }
                    // Replace with ComicInfo.xml file
                    val comicInfo = manga.getComicInfo()
                    mangaDir
                        .createFile(COMIC_INFO_FILE)
                        ?.openOutputStream()
                        ?.use {
                            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
                            it.write(comicInfoString.toByteArray())
                            legacyJsonDetailsFile.delete()
                        }
                }

                // Copy ComicInfo.xml from chapter archive to top level if found
                noXmlFile == null -> {
                    val chapterArchives = mangaDirFiles
                        .filter(Archive::isSupported)
                        .toList()

                    val copiedFile = copyComicInfoFileFromArchive(chapterArchives, mangaDir)
                    if (copiedFile != null) {
                        setMangaDetailsFromComicInfoFile(copiedFile.openInputStream(), manga)
                    } else {
                        // Avoid re-scanning
                        mangaDir.createFile(".noxml")
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error setting manga details from local metadata for ${manga.title}" }
        }

        return@withIOContext manga
    }

    private fun copyComicInfoFileFromArchive(chapterArchives: List<UniFile>, folder: UniFile): UniFile? {
        for (chapter in chapterArchives) {
            when (Format.valueOf(chapter)) {
                is Format.Zip -> {
                    chapter.openReadOnlyChannel(context).toZipFile().use { zip ->
                        zip.getEntry(COMIC_INFO_FILE)?.let { comicInfoFile ->
                            zip.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folder)
                            }
                        }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(chapter.openInputStream()).use { rar ->
                        rar.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }?.let { comicInfoFile ->
                            rar.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folder)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    private fun copyComicInfoFile(comicInfoFileStream: InputStream, folder: UniFile): UniFile? {
        return folder.createFile(COMIC_INFO_FILE)?.apply {
            openOutputStream().use { outputStream ->
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

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> = withIOContext {
        val chapters = fileSystem.getFilesInMangaDirectory(manga.url)
            // Only keep supported formats
            .filter { it.isDirectory || Archive.isSupported(it) }
            .map { chapterFile ->
                SChapter.create().apply {
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

                    val format = Format.valueOf(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file.openReadOnlyChannel(context)).use { epub ->
                            epub.fillMetadata(manga, this)
                        }
                    }
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }

        // Copy the cover from the first chapter found if not available
        if (manga.thumbnail_url.isNullOrBlank()) {
            chapters.lastOrNull()?.let { chapter ->
                updateCover(chapter, manga)
            }
        }

        chapters
    }

    // Filters
    override fun getFilterList(): FilterList {
        val genres = mangaChunks.flatten().mapNotNull { it.genre?.split(",") }
            .flatMap { it.map { genre -> genre.trim() } }.toSet()

        val authors = mangaChunks.flatten().mapNotNull { it.author?.split(",") }
            .flatMap { it.map { author -> author.trim() } }.toSet()

        val artists = mangaChunks.flatten().mapNotNull { it.artist?.split(",") }
            .flatMap { it.map { artist -> artist.trim() } }.toSet()

        val filters = try {
            mutableListOf<Filter<*>>(
                OrderBy.Popular(context),
                Separator(),
                GenreGroup(context, genres.map { GenreFilter(it) }),
                AuthorGroup(context, authors.map { AuthorFilter(it) }),
                ArtistGroup(context, artists.map { ArtistFilter(it) }),
                StatusGroup(
                    context,
                    listOf(
                        context.getString(R.string.ongoing),
                        context.getString(R.string.completed),
                        context.getString(R.string.licensed),
                        context.getString(R.string.publishing_finished),
                        context.getString(R.string.cancelled),
                        context.getString(R.string.on_hiatus),
                        context.getString(R.string.unknown),
                    ).map { StatusFilter(it) },
                ),
                Separator(),
                TextSearchHeader(context),
                GenreTextSearch(context),
                AuthorTextSearch(context),
                ArtistTextSearch(context),
                Separator(),
                LocalSourceInfoHeader(context),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
        return FilterList(filters)
    }

    // Unused stuff
    override suspend fun getPageList(chapter: SChapter) = throw UnsupportedOperationException("Unused")

    fun getFormat(chapter: SChapter): Format {
        try {
            val (mangaDirName, chapterName) = chapter.url.split('/', limit = 2)
            return fileSystem.getBaseDirectory()
                ?.findFile(mangaDirName, true)
                ?.findFile(chapterName, true)
                ?.let(Format.Companion::valueOf)
                ?: throw Exception(context.stringResource(MR.strings.chapter_not_found))
        } catch (e: Format.UnknownFormatException) {
            throw Exception(context.stringResource(MR.strings.local_invalid_format))
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): UniFile? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 ->
                            f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(
                                f2.name.orEmpty(),
                            )
                        }
                        ?.find {
                            !it.isDirectory && ImageUtil.isImage(it.name) { it.openInputStream() }
                        }

                    entry?.let { coverManager.update(manga, it.openInputStream()) }
                }
                is Format.Zip -> {
                    format.file.openReadOnlyChannel(context).toZipFile().use { zip ->
                        val entry = zip.entries.toList()
                            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, zip.getInputStream(it)) }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(format.file.openInputStream()).use { archive ->
                        val entry = archive.fileHeaders
                            .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, archive.getInputStream(it)) }
                    }
                }
                is Format.Epub -> {
                    EpubFile(format.file.openReadOnlyChannel(context)).use { epub ->
                        val entry = epub.getImagesFromPages()
                            .firstOrNull()
                            ?.let { epub.getEntry(it) }

                        entry?.let { coverManager.update(manga, epub.getInputStream(it)) }
                    }
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private const val CHUNK_SIZE = 10
    }
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID
