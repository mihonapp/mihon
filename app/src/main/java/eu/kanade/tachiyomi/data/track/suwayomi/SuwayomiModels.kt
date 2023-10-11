package eu.kanade.tachiyomi.data.track.suwayomi

import kotlinx.serialization.Serializable

@Serializable
data class SourceDataClass(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,

    /** The Source provides a latest listing */
    val supportsLatest: Boolean,

    /** The Source implements [ConfigurableSource] */
    val isConfigurable: Boolean,

    /** The Source class has a @Nsfw annotation */
    val isNsfw: Boolean,

    /** A nicer version of [name] */
    val displayName: String,
)

@Serializable
data class MangaDataClass(
    val id: Int,
    val sourceId: String,

    val url: String,
    val title: String,
    val thumbnailUrl: String?,

    val initialized: Boolean,

    val artist: String?,
    val author: String?,
    val description: String?,
    val genre: List<String>,
    val status: String,
    val inLibrary: Boolean,
    val inLibraryAt: Long,
    val source: SourceDataClass?,

    val meta: Map<String, String>,

    val realUrl: String?,
    val lastFetchedAt: Long?,
    val chaptersLastFetchedAt: Long?,

    val freshData: Boolean,
    val unreadCount: Long?,
    val downloadCount: Long?,
    val chapterCount: Long, // actually is nullable server side, but should be set at this time
    val lastChapterRead: ChapterDataClass?,

    val age: Long?,
    val chaptersAge: Long?,
)

@Serializable
data class ChapterDataClass(
    val id: Int,
    val url: String,
    val name: String,
    val uploadDate: Long,
    val chapterNumber: Float,
    val scanlator: String?,
    val mangaId: Int,

    /** chapter is read */
    val read: Boolean,

    /** chapter is bookmarked */
    val bookmarked: Boolean,

    /** last read page, zero means not read/no data */
    val lastPageRead: Int,

    /** last read page, zero means not read/no data */
    val lastReadAt: Long,

    /** this chapter's index, starts with 1 */
    val index: Int,

    /** the date we fist saw this chapter*/
    val fetchedAt: Long,

    /** is chapter downloaded */
    val downloaded: Boolean,

    /** used to construct pages in the front-end */
    val pageCount: Int,

    /** total chapter count, used to calculate if there's a next and prev chapter */
    val chapterCount: Int?,

    /** used to store client specific values */
    val meta: Map<String, String>,
)
