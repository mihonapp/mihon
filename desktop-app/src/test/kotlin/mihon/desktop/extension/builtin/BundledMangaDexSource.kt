package mihon.desktop.extension.builtin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.updates.DesktopAppUpdateService
import mihon.extension.source.WindowsHttpSource
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class BundledMangaDexSource(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) : WindowsHttpSource {

    override val id: Long = MANGADEX_SOURCE_ID
    override val name: String = "MangaDex"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://api.mangadex.org"

    override val headers: Map<String, String> = mapOf(
        "User-Agent" to "MihonW/${DesktopAppUpdateService.CURRENT_VERSION} (Windows NT 10.0; Win64; x64)",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        const val MANGADEX_SOURCE_ID = 2499283573021220255L
        private const val PAGE_LIMIT = 20
    }

    class ContentRatingFilter(
        name: String,
        val value: String,
        state: Boolean = false,
    ) : Filter.CheckBox(name, state)

    class ContentRatingGroup(
        ratings: List<ContentRatingFilter>,
    ) : Filter.Group<ContentRatingFilter>("Content Rating", ratings)

    class StatusFilter(
        name: String,
        val value: String,
        state: Boolean = false,
    ) : Filter.CheckBox(name, state)

    class StatusGroup(
        statuses: List<StatusFilter>,
    ) : Filter.Group<StatusFilter>("Publication Status", statuses)

    class SortFilter(
        values: Array<String> = arrayOf(
            "Most Follows",
            "Latest Upload",
            "Title",
            "Highest Rating",
            "Created At",
        ),
        state: Selection = Selection(0, false),
    ) : Filter.Sort("Sort By", values, state)

    class OriginalLanguageFilter(
        val valuesMap: List<Pair<String, String>> = listOf(
            "All" to "",
            "Japanese" to "ja",
            "Korean" to "ko",
            "Chinese" to "zh",
            "English" to "en",
        ),
        state: Int = 0,
    ) : Filter.Select<String>("Original Language", valuesMap.map { it.first }.toTypedArray(), state)

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        ContentRatingGroup(
            listOf(
                ContentRatingFilter("Safe", "safe", true),
                ContentRatingFilter("Suggestive", "suggestive", true),
                ContentRatingFilter("Erotica", "erotica", false),
                ContentRatingFilter("Pornographic", "pornographic", false),
            ),
        ),
        StatusGroup(
            listOf(
                StatusFilter("Ongoing", "ongoing", false),
                StatusFilter("Completed", "completed", false),
                StatusFilter("Hiatus", "hiatus", false),
                StatusFilter("Cancelled", "cancelled", false),
            ),
        ),
        OriginalLanguageFilter(),
    )

    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val offset = (page - 1) * PAGE_LIMIT
        val url = "$baseUrl/manga?limit=$PAGE_LIMIT&offset=$offset&includes[]=cover_art&order[followedCount]=desc" +
            "&contentRating[]=safe&contentRating[]=suggestive"
        fetchMangaList(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val offset = (page - 1) * PAGE_LIMIT
        val url =
            "$baseUrl/manga?limit=$PAGE_LIMIT&offset=$offset&includes[]=cover_art&order[latestUploadedChapter]=desc" +
                "&contentRating[]=safe&contentRating[]=suggestive"
        fetchMangaList(url)
    }

    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage = withContext(
        Dispatchers.IO,
    ) {
        val offset = (page - 1) * PAGE_LIMIT
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val urlBuilder = StringBuilder("$baseUrl/manga?limit=$PAGE_LIMIT&offset=$offset&includes[]=cover_art")
        if (encodedQuery.isNotBlank()) {
            urlBuilder.append("&title=").append(encodedQuery)
        }

        var hasRatings = false
        var hasOrder = false
        for (filter in filters) {
            when (filter) {
                is ContentRatingGroup -> {
                    val active = filter.state.filter { it.state }
                    if (active.isNotEmpty()) {
                        hasRatings = true
                        active.forEach { urlBuilder.append("&contentRating[]=").append(it.value) }
                    }
                }
                is StatusGroup -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.append("&status[]=").append(it.value)
                    }
                }
                is SortFilter -> {
                    val sel = filter.state
                    if (sel != null) {
                        hasOrder = true
                        val orderKey = when (sel.index) {
                            0 -> "followedCount"
                            1 -> "latestUploadedChapter"
                            2 -> "title"
                            3 -> "rating"
                            4 -> "createdAt"
                            else -> "followedCount"
                        }
                        val dir = if (sel.ascending) "asc" else "desc"
                        urlBuilder.append("&order[$orderKey]=").append(dir)
                    }
                }
                is OriginalLanguageFilter -> {
                    val code = filter.valuesMap.getOrNull(filter.state)?.second
                    if (!code.isNullOrBlank()) {
                        urlBuilder.append("&originalLanguage[]=").append(code)
                    }
                }
                else -> {}
            }
        }

        if (!hasRatings) {
            urlBuilder.append("&contentRating[]=safe&contentRating[]=suggestive")
        }
        if (!hasOrder) {
            urlBuilder.append("&order[followedCount]=desc")
        }

        fetchMangaList(urlBuilder.toString())
    }

    private fun fetchMangaList(url: String): MangasPage {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("MangaDex API error HTTP ${response.code}: ${response.message}")
        }

        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return MangasPage(emptyList(), false)
        val total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val offset = root["offset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

        val mangas = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val mangaId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attributes = obj["attributes"]?.jsonObject ?: return@mapNotNull null

            // Title
            val titleObj = attributes["title"]?.jsonObject
            val title = titleObj?.get("en")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: "Unknown Title"

            // Description
            val descObj = attributes["description"]?.jsonObject
            val desc = descObj?.get("en")?.jsonPrimitive?.contentOrNull
                ?: descObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: ""

            // Status
            val statusStr = attributes["status"]?.jsonPrimitive?.contentOrNull
            val status = when (statusStr) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                "cancelled" -> SManga.CANCELLED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            // Cover filename from relationships
            val relationships = obj["relationships"]?.jsonArray ?: emptyList()
            var coverFileName: String? = null
            for (rel in relationships) {
                val relObj = rel.jsonObject
                if (relObj["type"]?.jsonPrimitive?.contentOrNull == "cover_art") {
                    coverFileName = relObj["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
                    if (coverFileName != null) break
                }
            }

            val coverUrl = coverFileName?.let { fileName ->
                "https://uploads.mangadex.org/covers/$mangaId/$fileName.256.jpg"
            }

            SManga(
                url = "/manga/$mangaId",
                title = title,
                thumbnailUrl = coverUrl,
                description = desc,
                status = status,
                initialized = true,
            )
        }

        val hasNextPage = (offset + PAGE_LIMIT) < total
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        val mangaId = manga.url.removePrefix("/manga/").trim('/')
        val url = "$baseUrl/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist"

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return@withContext manga

        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val obj = root["data"]?.jsonObject ?: return@withContext manga
        val attributes = obj["attributes"]?.jsonObject ?: return@withContext manga

        // Title
        val titleObj = attributes["title"]?.jsonObject
        val title = titleObj?.get("en")?.jsonPrimitive?.contentOrNull
            ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: manga.title

        // Description
        val descObj = attributes["description"]?.jsonObject
        val desc = descObj?.get("en")?.jsonPrimitive?.contentOrNull
            ?: descObj?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: manga.description

        // Authors & Artists
        val relationships = obj["relationships"]?.jsonArray ?: emptyList()
        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        var coverFileName: String? = null

        for (rel in relationships) {
            val relObj = rel.jsonObject
            val type = relObj["type"]?.jsonPrimitive?.contentOrNull
            val name = relObj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            if (type == "author" && name != null) authors.add(name)
            if (type == "artist" && name != null) artists.add(name)
            if (type == "cover_art") {
                coverFileName = relObj["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
            }
        }

        val coverUrl = coverFileName?.let { fileName ->
            "https://uploads.mangadex.org/covers/$mangaId/$fileName.512.jpg"
        } ?: manga.thumbnailUrl

        manga.copy(
            title = title,
            description = desc,
            author = authors.firstOrNull() ?: manga.author,
            artist = artists.firstOrNull() ?: manga.artist,
            thumbnailUrl = coverUrl,
            initialized = true,
        )
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val mangaId = manga.url.removePrefix("/manga/").trim('/')
        val chapters = mutableListOf<SChapter>()
        var offset = 0
        val limit = 100
        var total = Int.MAX_VALUE

        while (offset < total && chapters.size < 500) {
            val url = "$baseUrl/manga/$mangaId/feed?limit=$limit&offset=$offset&includes[]=scanlation_group" +
                "&order[chapter]=desc&includeExternalUrl=0"

            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) break

            val body = response.body.string()
            val root = json.parseToJsonElement(body).jsonObject
            val dataArray = root["data"]?.jsonArray ?: break
            total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

            for (element in dataArray) {
                val obj = element.jsonObject
                val chapterId = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val attributes = obj["attributes"]?.jsonObject ?: continue

                val chNum = attributes["chapter"]?.jsonPrimitive?.contentOrNull
                val chTitle = attributes["title"]?.jsonPrimitive?.contentOrNull
                val lang = attributes["translatedLanguage"]?.jsonPrimitive?.contentOrNull ?: "en"

                val name = when {
                    !chNum.isNullOrBlank() && !chTitle.isNullOrBlank() -> "Ch. $chNum - $chTitle ($lang)"
                    !chNum.isNullOrBlank() -> "Ch. $chNum ($lang)"
                    !chTitle.isNullOrBlank() -> "$chTitle ($lang)"
                    else -> "Chapter ($lang)"
                }

                val publishAt = attributes["publishAt"]?.jsonPrimitive?.contentOrNull
                val uploadDate = publishAt?.let {
                    try {
                        Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(it)).toEpochMilli()
                    } catch (_: Exception) {
                        0L
                    }
                } ?: 0L

                chapters.add(
                    SChapter(
                        url = "/chapter/$chapterId",
                        name = name,
                        chapterNumber = chNum?.toFloatOrNull() ?: -1f,
                        dateUpload = uploadDate,
                    ),
                )
            }

            offset += limit
            if (dataArray.isEmpty()) break
        }

        chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val chapterId = chapter.url.removePrefix("/chapter/").trim('/')
        val url = "$baseUrl/at-home/server/$chapterId"

        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to load MangaDex chapter pages: HTTP ${response.code}")
        }

        val body = response.body.string()
        val root = json.parseToJsonElement(body).jsonObject
        val baseUrlHost = root["baseUrl"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Missing baseUrl in MangaDex At-Home response")
        val chapterObj = root["chapter"]?.jsonObject
            ?: throw IllegalStateException("Missing chapter object in MangaDex response")
        val hash = chapterObj["hash"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("Missing hash in MangaDex response")
        val originalFiles = chapterObj["data"]?.jsonArray?.takeIf { it.isNotEmpty() }
        val dataFiles = originalFiles
            ?: chapterObj["dataSaver"]?.jsonArray
            ?: return@withContext emptyList()
        val directory = if (originalFiles != null) "data" else "data-saver"

        dataFiles.mapIndexed { index, fileNameElement ->
            val fileName = fileNameElement.jsonPrimitive.content
            val imageUrl = "$baseUrlHost/$directory/$hash/$fileName"
            Page(
                index = index,
                url = chapter.url,
                imageUrl = imageUrl,
                headers = headers + ("Referer" to "https://mangadex.org/"),
            )
        }
    }
}
