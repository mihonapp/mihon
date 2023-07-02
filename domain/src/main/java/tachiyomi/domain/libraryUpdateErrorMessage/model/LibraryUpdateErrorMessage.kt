package tachiyomi.domain.libraryUpdateErrorMessage.model

import java.io.Serializable

data class LibraryUpdateErrorMessage(
    val id: Long,
    val message: String,
) : Serializable
