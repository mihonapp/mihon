package tachiyomi.domain.readinglist.cbl.model

data class CblReadingList(
    val name: String?,
    val description: String?,
    val declaredIssueCount: Int?,
    val books: List<CblBook>,
    val extraAttributes: Map<String, String> = emptyMap(),
    val extraElements: Map<String, List<String>> = emptyMap(),
    val warnings: List<CblParseWarning> = emptyList(),
)

data class CblBook(
    val position: Int,
    val series: String,
    val number: String,
    val volume: String?,
    val year: String?,
    val databases: List<CblDatabaseReference> = emptyList(),
    val extraAttributes: Map<String, String> = emptyMap(),
    val extraElements: Map<String, List<String>> = emptyMap(),
)

data class CblDatabaseReference(
    val name: String?,
    val seriesId: String?,
    val issueId: String?,
    val extraAttributes: Map<String, String> = emptyMap(),
    val extraElements: Map<String, List<String>> = emptyMap(),
)

data class CblParseWarning(
    val code: CblParseWarningCode,
    val message: String,
)

enum class CblParseWarningCode {
    MISSING_NAME,
    EMPTY_LIST,
    INVALID_DECLARED_ISSUE_COUNT,
    ISSUE_COUNT_MISMATCH,
}

data class CblParserLimits(
    val maxCharacters: Int = 8 * 1024 * 1024,
    val maxBooks: Int = 100_000,
) {
    init {
        require(maxCharacters > 0) { "maxCharacters must be positive" }
        require(maxBooks > 0) { "maxBooks must be positive" }
    }
}

class CblParseException(
    val failure: CblParseFailure,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

enum class CblParseFailure {
    EMPTY_INPUT,
    INPUT_TOO_LARGE,
    UNSAFE_XML,
    MALFORMED_XML,
    INVALID_ROOT,
    MULTIPLE_ROOTS,
    TOO_MANY_BOOKS,
    MISSING_BOOK_ATTRIBUTE,
    INVALID_STRUCTURE,
}
