package eu.kanade.tachiyomi.data.track.novelupdates

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import eu.kanade.domain.track.service.TrackPreferences
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * NovelUpdates tracker implementation.
 * Website: https://www.novelupdates.com
 *
 * NovelUpdates is a web-based reading list tracker for web novels.
 * It uses cookie-based authentication from the website.
 *
 * Status mapping from reading list IDs:
 * - 0: Reading (CURRENT)
 * - 1: Completed
 * - 2: Plan to Read (PLANNING)
 * - 3: On Hold (PAUSED)
 * - 4: Dropped
 * - 5: Dropped (alternative)
 */
class NovelUpdates(id: Long) : BaseTracker(id, "NovelUpdates") {

    private val json: Json by injectLazy()
    private val baseUrl = "https://www.novelupdates.com"

    override fun getLogo() = R.drawable.ic_tracker_novelupdates

    override fun getLogoColor(): Int = Color.parseColor("#15A8E6")

    override fun getStatusList() = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    override fun getStatus(status: Long): StringResource? {
        return when (status) {
            READING -> MR.strings.reading
            COMPLETED -> MR.strings.completed
            ON_HOLD -> MR.strings.on_hold
            DROPPED -> MR.strings.dropped
            PLAN_TO_READ -> MR.strings.plan_to_read
            else -> null
        }
    }

    override fun getReadingStatus() = READING
    override fun getRereadingStatus() = READING
    override fun getCompletionStatus() = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf(
        "10", "9", "8", "7", "6", "5", "4", "3", "2", "1", "",
    )

    override fun indexToScore(index: Int): Double {
        return if (index == 10) 0.0 else (10 - index).toDouble()
    }

    override fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

    override fun displayScore(track: DomainTrack): String {
        return if (track.score == 0.0) "-" else track.score.toInt().toString()
    }

    /**
     * Get authenticated headers using stored cookies.
     */
    private fun getAuthHeaders(): Headers {
        val cookies = getPassword()
        return Headers.Builder()
            .add("Cookie", cookies)
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:141.0) Gecko/20100101 Firefox/141.0")
            .add("Referer", "$baseUrl/")
            .build()
    }

    /**
     * Get the numeric novel ID from a series page.
     */
    private suspend fun getNovelId(seriesUrl: String): String? {
        return try {
            val response = client.newCall(GET(seriesUrl, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()

            // Try shortlink meta
            val shortlink = document.select("link[rel=shortlink]").attr("href")
            val shortlinkMatch = Regex("p=(\\d+)").find(shortlink)
            if (shortlinkMatch != null) return shortlinkMatch.groupValues[1]

            // Try activity stats link
            val activityLink = document.select("a[href*=activity-stats]").attr("href")
            val activityMatch = Regex("seriesid=(\\d+)").find(activityLink)
            if (activityMatch != null) return activityMatch.groupValues[1]

            // Try hidden input
            val postId = document.select("input#mypostid").attr("value")
            if (postId.isNotEmpty()) return postId

            null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get novel ID from $seriesUrl" }
            null
        }
    }

    /**
     * Get the user's current reading list status for a novel.
     */
    private suspend fun getReadingListStatus(novelId: String): Long? {
        return try {
            val response = client.newCall(
                GET("$baseUrl/series/?p=$novelId", getAuthHeaders()),
            ).awaitSuccess()
            val document = response.asJsoup()

            val sticon = document.select("div.sticon")
            // If "addme.png" is present, novel is not on any list
            if (sticon.select("img[src*=addme.png]").isNotEmpty()) {
                return null
            }

            // Extract list ID from reading list link
            val listLink = sticon.select("span.sttitle a").attr("href")
            val listMatch = Regex("list=(\\d+)").find(listLink)
            listMatch?.groupValues?.get(1)?.toLongOrNull()?.let { listId ->
                // Map NovelUpdates list IDs to our status
                when (listId) {
                    0L -> READING
                    1L -> COMPLETED
                    2L -> PLAN_TO_READ
                    3L -> ON_HOLD
                    4L, 5L -> DROPPED
                    else -> READING
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get reading list status" }
            null
        }
    }

    /**
     * Get chapter progress from notes.
     */
    private suspend fun getNotesProgress(novelId: String): Int {
        return try {
            val formBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val request = POST(
                "$baseUrl/wp-admin/admin-ajax.php",
                getAuthHeaders(),
                formBody,
            )

            val response = client.newCall(request).awaitSuccess()
            val responseText = response.body.string()

            // Clean response (may end with "0")
            val cleanedText = responseText.trim().replace(Regex("}\\s*0+$"), "}")

            // Parse JSON and extract notes
            val notesMatch = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
            val notes = notesMatch?.groupValues?.get(1) ?: return 0

            // Find chapter count in notes
            val chapterMatch = Regex("total\\s+chapters\\s+read:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(notes)
            chapterMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get notes progress" }
            0
        }
    }

    /**
     * Update chapter progress in notes.
     */
    private suspend fun updateNotesProgress(novelId: String, chapters: Int) {
        try {
            // First get existing notes
            val getNotesBody = FormBody.Builder()
                .add("action", "wi_notestagsfic")
                .add("strSID", novelId)
                .build()

            val getRequest = POST(
                "$baseUrl/wp-admin/admin-ajax.php",
                getAuthHeaders(),
                getNotesBody,
            )

            val getResponse = client.newCall(getRequest).awaitSuccess()
            val responseText = getResponse.body.string()
            val cleanedText = responseText.trim().replace(Regex("}\\s*0+$"), "}")

            // Extract existing notes and tags
            val notesMatch = Regex("\"notes\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
            val tagsMatch = Regex("\"tags\"\\s*:\\s*\"([^\"]+)\"").find(cleanedText)
            val existingNotes = notesMatch?.groupValues?.get(1) ?: ""
            val existingTags = tagsMatch?.groupValues?.get(1) ?: ""

            // Update or add chapter count
            val chapterPattern = Regex("total\\s+chapters\\s+read:\\s*\\d+", RegexOption.IGNORE_CASE)
            val replacement = "total chapters read: $chapters"
            val updatedNotes = if (chapterPattern.containsMatchIn(existingNotes)) {
                existingNotes.replace(chapterPattern, replacement)
            } else {
                if (existingNotes.isEmpty()) {
                    replacement
                } else {
                    "$existingNotes<br/>$replacement"
                }
            }

            // Save updated notes
            val updateBody = FormBody.Builder()
                .add("action", "wi_rlnotes")
                .add("strSID", novelId)
                .add("strNotes", updatedNotes)
                .add("strTags", existingTags)
                .build()

            val updateRequest = POST(
                "$baseUrl/wp-admin/admin-ajax.php",
                getAuthHeaders(),
                updateBody,
            )

            client.newCall(updateRequest).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update notes progress" }
        }
    }

    /**
     * Move novel to a reading list.
     */
    private suspend fun moveToList(novelId: String, listId: Long) {
        try {
            val request = GET(
                "$baseUrl/updatelist.php?sid=$novelId&lid=$listId&act=move",
                getAuthHeaders(),
            )
            client.newCall(request).awaitSuccess()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to move to list" }
        }
    }

    /**
     * Convert our status to NovelUpdates list ID.
     * Uses custom mapping from preferences if enabled.
     */
    private fun statusToListId(status: Long): Long {
        // Check custom mapping first
        try {
            if (trackPreferences.novelUpdatesUseCustomListMapping().get()) {
                val json = trackPreferences.novelUpdatesCustomListMapping().get()
                if (json.isNotEmpty() && json != "{}") {
                    val mappings = Json.decodeFromString<Map<String, String>>(json)
                    val listId = mappings[status.toString()]
                    if (listId != null) return listId.toLong()
                }
            }
        } catch (_: Exception) {}

        return when (status) {
            READING -> 0L
            COMPLETED -> 1L
            PLAN_TO_READ -> 2L
            ON_HOLD -> 3L
            DROPPED -> 4L
            else -> 0L
        }
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        val novelId = track.remote_id.toString()

        // Update reading list status
        moveToList(novelId, statusToListId(track.status))

        // Update chapter progress in notes
        if (track.last_chapter_read > 0) {
            updateNotesProgress(novelId, track.last_chapter_read.toInt())
        }

        return track
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val slug = Regex("series/([^/]+)/?").find(track.tracking_url)?.groupValues?.get(1)
        if (slug != null) {
            val novelId = getNovelId("$baseUrl/series/$slug/")
            if (novelId != null) {
                track.remote_id = novelId.toLongOrNull() ?: track.remote_id
            }
        }

        // Check current list status
        val currentStatus = getReadingListStatus(track.remote_id.toString())
        track.status = currentStatus ?: if (hasReadChapters) READING else PLAN_TO_READ

        // Get progress from notes
        val progress = getNotesProgress(track.remote_id.toString())
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    override suspend fun search(query: String): List<TrackSearch> {
        // Use series-finder with proper parameters for better search results
        val encodedQuery = query.replace(" ", "+")
        val url = "$baseUrl/series-finder/?sf=1&sh=$encodedQuery&sort=sdate&order=desc"
        return try {
            val response = client.newCall(GET(url, getAuthHeaders())).awaitSuccess()
            val document = response.asJsoup()
            document.select("div.search_main_box_nu").map { element ->
                val track = TrackSearch.create(id)
                val titleElement = element.select("div.search_title a").first()
                    ?: element.select(".search_title a").first()
                track.title = titleElement?.text()?.trim() ?: ""
                track.tracking_url = titleElement?.attr("href") ?: ""

                // Try to get numeric ID from the page
                val sidSpan = element.select("span[id^=sid]").first()
                val sidId = sidSpan?.attr("id")
                val numericId = sidId?.let {
                    Regex("sid(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull()
                }

                // Extract slug from URL
                val slug = track.tracking_url.let {
                    Regex("series/([^/]+)/?").find(it)?.groupValues?.get(1) ?: ""
                }

                // Use numeric ID if available, otherwise hash the slug
                track.remote_id = numericId ?: slug.hashCode().toLong().let { if (it < 0) -it else it }

                // Get cover image
                val coverImg = element.select("div.search_img_nu img, .search_img_nu img").first()
                track.cover_url = coverImg?.attr("src")?.let { src ->
                    if (src.startsWith("http")) src else "$baseUrl$src"
                } ?: ""

                // Get description (handling collapsed text)
                val descContainer = element.select("div.search_body_nu").first()
                val hiddenText = descContainer?.select(".testhide")?.text() ?: ""
                val visibleText = descContainer?.text()?.replace(hiddenText, "")?.trim() ?: ""
                track.summary = (visibleText + " " + hiddenText)
                    .replace("... more>>", "")
                    .replace("<<less", "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                // Try to get publishing status from genre tags
                val genreText = element.select("div.search_genre, .search_genre").text()
                track.publishing_status = when {
                    genreText.contains("Completed", ignoreCase = true) -> "Completed"
                    genreText.contains("Ongoing", ignoreCase = true) -> "Ongoing"
                    else -> ""
                }
                track
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "NovelUpdates search failed" }
            emptyList()
        }
    }

    override suspend fun refresh(track: Track): Track {
        val novelId = track.remote_id.toString()

        // Get current status from website
        val status = getReadingListStatus(novelId)
        if (status != null) {
            track.status = status
        }

        // Get progress from notes
        val progress = getNotesProgress(novelId)
        if (progress > 0) {
            track.last_chapter_read = progress.toDouble()
        }

        return track
    }

    /**
     * Get all available custom reading lists for the user.
     * Scrapes the reading list page to find all lists.
     */
    suspend fun getAvailableReadingLists(): List<Pair<String, String>> {
        return try {
            val response = client.newCall(
                GET("$baseUrl/reading-list/", getAuthHeaders()),
            ).awaitSuccess()
            val document = response.asJsoup()
            
            val lists = mutableListOf<Pair<String, String>>()
            
            // Try menu lists first
            document.select("div#cssmenu li a").forEach { link ->
                val href = link.attr("href")
                val text = link.text().trim()
                
                if (href.contains("reading-list/?list=")) {
                    val listMatch = Regex("list=(\\d+)").find(href)
                    val listId = listMatch?.groupValues?.get(1)
                    if (listId != null && text.isNotEmpty()) {
                        lists.add(Pair(listId, text))
                    }
                }
            }
            
            // If no menu lists, try select dropdown
            if (lists.isEmpty()) {
                document.select("div.sticon select.stmove option").forEach { option ->
                    val value = option.attr("value")
                    val text = option.text().trim()
                    
                    if (value.isNotEmpty() && value != "---" && value != "Select..." && text.isNotEmpty()) {
                        lists.add(Pair(value, text))
                    }
                }
            }
            
            lists
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to get available reading lists" }
            emptyList()
        }
    }

    override suspend fun login(username: String, password: String) {
        // NovelUpdates uses cookie-based auth from the website
        // The password field stores session cookies
        saveCredentials(username, password)
    }

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 5L
    }
}
