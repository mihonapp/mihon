package tachiyomi.data.libraryUpdateErrorMessage

import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage

val LibraryUpdateErrorMessageMapper: (Long, String) -> LibraryUpdateErrorMessage = { id, message ->
    LibraryUpdateErrorMessage(
        id = id,
        message = message,
    )
}
