package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

@Serializable
data class KitsuUpdateMangaResult(
    // yes there are two different error attributes and yes they have different structures
    // it seems both are valid in different cases
    // (500s send "error" for example, 200s with validation errors send "errors")
    val data: KitsuUpdateMangaData?,
    val errors: List<KitsuErrorMessage>?,
    val error: KitsuErrorMessage?,
)

@Serializable
data class KitsuUpdateMangaData(
    val libraryEntry: KitsuUpdateMangaLibraryEntryWrapper,
)

@Serializable
data class KitsuUpdateMangaLibraryEntryWrapper(
    val update: KitsuLibraryEntryResult,
)
