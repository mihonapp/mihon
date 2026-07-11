package tachiyomi.data.readinglist

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.readinglist.cbl.model.CblParseWarning
import tachiyomi.domain.readinglist.cbl.model.CblParseWarningCode
import tachiyomi.domain.readinglist.cbl.model.CblReadingList

internal class ReadingListStorageCodec(
    private val json: Json,
) {

    fun encodeAttributes(value: Map<String, String>): String {
        return json.encodeToString(value)
    }

    fun decodeAttributes(value: String): Map<String, String> {
        return json.decodeFromString(value)
    }

    fun encodeElements(value: Map<String, List<String>>): String {
        return json.encodeToString(value)
    }

    fun decodeElements(value: String): Map<String, List<String>> {
        return json.decodeFromString(value)
    }

    fun encodeWarnings(value: List<CblParseWarning>): String {
        return json.encodeToString(
            value.map { warning ->
                StoredWarning(
                    code = warning.code.name,
                    message = warning.message,
                )
            },
        )
    }

    fun decodeWarnings(value: String): List<CblParseWarning> {
        return json.decodeFromString<List<StoredWarning>>(value).map { warning ->
            CblParseWarning(
                code = CblParseWarningCode.valueOf(warning.code),
                message = warning.message,
            )
        }
    }

    @Serializable
    private data class StoredWarning(
        val code: String,
        val message: String,
    )
}

internal fun CblReadingList.requireValidPersistenceOrder() {
    books.forEachIndexed { index, book ->
        require(book.position == index) {
            "CBL book position ${book.position} does not match authoritative list index $index"
        }
        require(book.series.isNotBlank()) {
            "CBL book at position $index has a blank series"
        }
        require(book.number.isNotBlank()) {
            "CBL book at position $index has a blank issue number"
        }
    }
}
