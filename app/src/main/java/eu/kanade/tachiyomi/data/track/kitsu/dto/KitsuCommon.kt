package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuErrorMessage(
    val message: String?,
)

@Serializable
data class KitsuLibraryEntryResult(
    val libraryEntry: KitsuSparseLibraryEntry,
)

@Serializable
data class KitsuSparseLibraryEntry(
    val id: String,
)
