package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuAddMangaResult(
    // yes there are two different error attributes and yes they have different structures
    // it seems both are valid in different cases
    // (500s send "error" for example, 200s with validation errors send "errors")
    val data: KitsuAddMangaData?,
    val errors: List<KitsuErrorMessage>?,
    val error: KitsuErrorMessage?,
)

@Serializable
data class KitsuAddMangaData(
    val libraryEntry: KitsuAddMangaLibraryEntryWrapper,
)

@Serializable
data class KitsuAddMangaLibraryEntryWrapper(
    val create: KitsuLibraryEntryResult,
)
