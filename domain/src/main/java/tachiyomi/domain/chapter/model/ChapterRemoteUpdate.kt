package tachiyomi.domain.chapter.model

import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

data class ChapterRemoteUpdate(
    val id: Long,
    val name: String,
    val scanlator: String?,
    val chapterNumber: Double,
    val dateUpload: Instant?,
    val sourceOrder: Long,
    val memo: JsonObject,
)
