package tachiyomi.domain.manga.model

import android.annotation.SuppressLint
import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.chapter.model.BookmarkColor
import java.io.ObjectStreamException
import kotlin.time.Instant
import java.io.Serializable as JavaSerializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Immutable
data class Manga(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val notes: String,
    val memo: JsonObject,
) : JavaSerializable {

    val expectedNextUpdate: Instant?
        get() = nextUpdate
            .takeIf { status != SManga.COMPLETED.toLong() }
            ?.let { Instant.fromEpochMilliseconds(it) }

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val bookmarkColorFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARK_COLOR_MASK

    val includedBookmarkColors: Set<BookmarkColor>
        get() = bookmarkColorsFromFlags(bookmarkColorFilterRaw)

    fun includesBookmarkColor(color: BookmarkColor): Boolean {
        val raw = bookmarkColorFilterRaw
        return raw == 0L || raw and colorToFlag(color) != 0L
    }

    val unreadFilter: TriState
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriState.ENABLED_IS
            CHAPTER_SHOW_READ -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val bookmarkedFilter: TriState
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_BOOKMARK_COLOR_SHIFT = 10
        const val CHAPTER_BOOKMARK_COLOR_MASK = 0x0003FC00L

        fun colorToFlag(color: BookmarkColor): Long = 1L shl (CHAPTER_BOOKMARK_COLOR_SHIFT + color.value)

        fun bookmarkColorsToFlags(colors: Set<BookmarkColor>): Long {
            if (colors.isEmpty() || colors.size == BookmarkColor.entries.size) return 0L
            return colors.fold(0L) { acc, color -> acc or colorToFlag(color) }
        }

        fun bookmarkColorsFromFlags(flags: Long): Set<BookmarkColor> {
            val raw = flags and CHAPTER_BOOKMARK_COLOR_MASK
            if (raw == 0L) return BookmarkColor.entries.toSet()
            return BookmarkColor.entries.filter { raw and colorToFlag(it) != 0L }.toSet()
        }

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_ALPHABET = 0x00000300L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        fun create() = Manga(
            id = -1L,
            url = "",
            title = "",
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            notes = "",
            memo = JsonObject.EMPTY,
        )
    }

    @Throws(ObjectStreamException::class)
    private fun writeReplace(): Any {
        return JavaToKotlinXSerializable(Json.encodeToString<Manga>(this))
    }

    class JavaToKotlinXSerializable(private val data: String) : JavaSerializable {

        @Throws(ObjectStreamException::class)
        private fun readResolve(): Any {
            return Json.decodeFromString<Manga>(data)
        }
    }
}
