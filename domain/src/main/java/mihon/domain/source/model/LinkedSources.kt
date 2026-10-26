package mihon.domain.source.model

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

// App-owned keys are kept outside the extension's metadata when making source requests.
const val LINKED_SOURCES_KEY = "mihon.linkedSources"
const val STORAGE_SOURCES_KEY = "mihon.storageSources"
private const val ASSIGNED_SOURCE_KEY = "mihon.assignedChapterSource"
private const val CHAPTER_ORIGINS_KEY = "mihon.chapterOrigins"
private val sourceJson = Json { ignoreUnknownKeys = true }

@Serializable
data class LinkedSourceManga(
    val sourceId: Long,
    val url: String,
    val title: String,
    val memo: JsonObject = JsonObject(emptyMap()),
    val scanlators: Set<String>? = null,
)

fun LinkedSourceManga.acceptsScanlator(scanlator: String?): Boolean = scanlators?.any {
    it.trim().equals(scanlator.orEmpty().trim(), ignoreCase = true)
} ?: true

@Serializable
data class ChapterOrigin(
    val sourceId: Long,
    val url: String,
    val name: String,
    val scanlator: String?,
    val dateUpload: Long,
    val chapterNumber: Float,
    val memo: JsonObject,
) {
    fun toSChapter(): SChapter = SChapter.create().also {
        it.url = url
        it.name = name
        it.scanlator = scanlator
        it.date_upload = dateUpload
        it.chapter_number = chapterNumber
        it.memo = memo
    }

    companion object {
        fun from(sourceId: Long, chapter: SChapter) = ChapterOrigin(
            sourceId,
            chapter.url,
            chapter.name,
            chapter.scanlator,
            chapter.date_upload,
            chapter.chapter_number,
            chapter.memo,
        )
    }
}

fun Manga.linkedSources(): List<LinkedSourceManga> = memo.linkedSources()

fun JsonObject.linkedSources(): List<LinkedSourceManga> = this[LINKED_SOURCES_KEY]?.let {
    runCatching { sourceJson.decodeFromJsonElement<List<LinkedSourceManga>>(it) }.getOrNull()
}.orEmpty()

fun JsonObject.withLinkedSources(sources: List<LinkedSourceManga>): JsonObject = JsonObject(
    this + (LINKED_SOURCES_KEY to sourceJson.encodeToJsonElement(sources)),
)

fun JsonObject.storageSources(primarySourceId: Long): Set<Long> {
    val saved = this[STORAGE_SOURCES_KEY]?.let {
        runCatching { sourceJson.decodeFromJsonElement<List<Long>>(it) }.getOrNull()
    }.orEmpty()
    return (listOf(primarySourceId) + saved + linkedSources().map { it.sourceId }).toSet()
}

fun Manga.storageSources(): Set<Long> = memo.storageSources(source)

fun JsonObject.withStorageSources(sources: Set<Long>): JsonObject = JsonObject(
    this + (STORAGE_SOURCES_KEY to sourceJson.encodeToJsonElement(sources.toList())),
)

fun JsonObject.withoutLinkedSources(): JsonObject = JsonObject(this - LINKED_SOURCES_KEY - STORAGE_SOURCES_KEY)

fun Chapter.origins(): List<ChapterOrigin> = memo.chapterOrigins()

fun JsonObject.chapterOrigins(): List<ChapterOrigin> = this[CHAPTER_ORIGINS_KEY]?.let {
    runCatching { sourceJson.decodeFromJsonElement<List<ChapterOrigin>>(it) }.getOrNull()
}.orEmpty()

fun JsonObject.withChapterOrigins(origins: List<ChapterOrigin>): JsonObject = JsonObject(
    this + (CHAPTER_ORIGINS_KEY to sourceJson.encodeToJsonElement(origins)),
)

fun JsonObject.withoutChapterOrigins(): JsonObject = JsonObject(this - CHAPTER_ORIGINS_KEY - ASSIGNED_SOURCE_KEY)

/** An origin describes an available version; it becomes assigned only when queued for download. */
fun JsonObject.assignedSourceId(): Long? = (this[ASSIGNED_SOURCE_KEY] as? JsonPrimitive)?.longOrNull

fun Chapter.assignedSourceId(): Long? = memo.assignedSourceId()

fun JsonObject.withAssignedSource(sourceId: Long?): JsonObject = JsonObject(
    if (sourceId == null) this - ASSIGNED_SOURCE_KEY else this + (ASSIGNED_SOURCE_KEY to JsonPrimitive(sourceId)),
)

// Unassigned chapters still need a representative origin for metadata and streaming requests.
fun Chapter.sourceId(defaultSourceId: Long): Long =
    assignedSourceId() ?: origins().firstOrNull()?.sourceId ?: defaultSourceId
