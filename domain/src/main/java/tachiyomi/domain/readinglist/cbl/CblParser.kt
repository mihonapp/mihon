package tachiyomi.domain.readinglist.cbl

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import tachiyomi.domain.readinglist.cbl.model.CblBook
import tachiyomi.domain.readinglist.cbl.model.CblDatabaseReference
import tachiyomi.domain.readinglist.cbl.model.CblParseException
import tachiyomi.domain.readinglist.cbl.model.CblParseFailure
import tachiyomi.domain.readinglist.cbl.model.CblParseWarning
import tachiyomi.domain.readinglist.cbl.model.CblParseWarningCode
import tachiyomi.domain.readinglist.cbl.model.CblParserLimits
import tachiyomi.domain.readinglist.cbl.model.CblReadingList

class CblParser(
    private val limits: CblParserLimits = CblParserLimits(),
    private val readerFactory: (String) -> XmlReader = { xml: String -> xmlStreaming.newReader(xml) },
) {

    fun parse(xml: String): CblReadingList {
        validateInput(xml)

        var reader: XmlReader? = null
        try {
            reader = readerFactory(xml)
            return parse(reader)
        } catch (error: CblParseException) {
            throw error
        } catch (error: Exception) {
            throw CblParseException(
                failure = CblParseFailure.MALFORMED_XML,
                message = "The CBL document is not valid XML",
                cause = error,
            )
        } finally {
            reader?.close()
        }
    }

    private fun parse(reader: XmlReader): CblReadingList {
        val frames = mutableListOf<ElementFrame>()
        val books = mutableListOf<CblBook>()
        val warnings = mutableListOf<CblParseWarning>()
        val descriptionParts = mutableListOf<String>()
        val rootExtraElements = linkedMapOf<String, MutableList<String>>()

        var rootSeen = false
        var rootClosed = false
        var rootAttributes = emptyMap<String, String>()
        var name: String? = null
        var declaredIssueCount: Int? = null
        var currentBook: BookBuilder? = null
        var currentDatabase: DatabaseBuilder? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    if (frames.isEmpty()) {
                        when {
                            !rootSeen -> {
                                if (reader.localName != READING_LIST_ELEMENT) {
                                    throw CblParseException(
                                        failure = CblParseFailure.INVALID_ROOT,
                                        message = "Expected <$READING_LIST_ELEMENT> as the CBL root element",
                                    )
                                }
                                rootSeen = true
                                rootAttributes = reader.readAttributes()
                            }
                            rootClosed -> {
                                throw CblParseException(
                                    failure = CblParseFailure.MULTIPLE_ROOTS,
                                    message = "The CBL document contains more than one root element",
                                )
                            }
                        }
                    }

                    val parentName = frames.lastOrNull()?.name
                    when (reader.localName) {
                        BOOK_ELEMENT -> {
                            if (currentBook != null) {
                                throw invalidStructure("Nested <$BOOK_ELEMENT> elements are not supported")
                            }
                            if (books.size >= limits.maxBooks) {
                                throw CblParseException(
                                    failure = CblParseFailure.TOO_MANY_BOOKS,
                                    message = "The CBL document exceeds the ${limits.maxBooks} entry limit",
                                )
                            }
                            currentBook = BookBuilder.from(reader.readAttributes(), books.size)
                        }
                        DATABASE_ELEMENT -> {
                            if (currentBook == null || parentName != BOOK_ELEMENT) {
                                throw invalidStructure("<$DATABASE_ELEMENT> must be a direct child of <$BOOK_ELEMENT>")
                            }
                            if (currentDatabase != null) {
                                throw invalidStructure("Nested <$DATABASE_ELEMENT> elements are not supported")
                            }
                            currentDatabase = DatabaseBuilder.from(reader.readAttributes())
                        }
                    }
                    frames += ElementFrame(reader.localName)
                }

                EventType.END_ELEMENT -> {
                    val frame = frames.removeLastOrNull()
                        ?: throw invalidStructure("Unexpected closing element </${reader.localName}>")
                    if (frame.name != reader.localName) {
                        throw invalidStructure(
                            "Closing element </${reader.localName}> does not match <${frame.name}>",
                        )
                    }

                    val parentName = frames.lastOrNull()?.name
                    val text = frame.text.toString().trim()

                    when {
                        frame.name == NAME_ELEMENT && parentName == READING_LIST_ELEMENT -> {
                            if (name == null) name = text.takeIf(String::isNotEmpty)
                        }
                        frame.name == DESCRIPTION_ELEMENT && parentName == READING_LIST_ELEMENT -> {
                            text.takeIf(String::isNotEmpty)?.let(descriptionParts::add)
                        }
                        frame.name == NOTES_ELEMENT && parentName == READING_LIST_ELEMENT -> {
                            text.takeIf(String::isNotEmpty)?.let(descriptionParts::add)
                        }
                        frame.name == ISSUE_COUNT_ELEMENT && parentName == READING_LIST_ELEMENT -> {
                            if (declaredIssueCount == null && text.isNotEmpty()) {
                                declaredIssueCount = text.toIntOrNull()?.takeIf { it >= 0 }
                                if (declaredIssueCount == null) {
                                    warnings += CblParseWarning(
                                        code = CblParseWarningCode.INVALID_DECLARED_ISSUE_COUNT,
                                        message = "NumIssues '$text' is not a non-negative integer",
                                    )
                                }
                            }
                        }
                        frame.name == DATABASE_ELEMENT -> {
                            val database = currentDatabase
                                ?: throw invalidStructure("Closed <$DATABASE_ELEMENT> without an active database")
                            currentBook?.databases?.add(database.build())
                                ?: throw invalidStructure("Closed <$DATABASE_ELEMENT> outside <$BOOK_ELEMENT>")
                            currentDatabase = null
                        }
                        frame.name == BOOK_ELEMENT -> {
                            if (currentDatabase != null) {
                                throw invalidStructure("Closed <$BOOK_ELEMENT> before <$DATABASE_ELEMENT>")
                            }
                            val book = currentBook
                                ?: throw invalidStructure("Closed <$BOOK_ELEMENT> without an active book")
                            books += book.build()
                            currentBook = null
                        }
                        parentName == DATABASE_ELEMENT && currentDatabase != null -> {
                            currentDatabase.extraElements.addValue(frame.name, text)
                        }
                        parentName == BOOK_ELEMENT && currentBook != null -> {
                            currentBook.extraElements.addValue(frame.name, text)
                        }
                        parentName == READING_LIST_ELEMENT &&
                            frame.name !in KNOWN_READING_LIST_CHILDREN -> {
                            rootExtraElements.addValue(frame.name, text)
                        }
                    }

                    if (frame.name == READING_LIST_ELEMENT && frames.isEmpty()) {
                        rootClosed = true
                    }
                }

                EventType.TEXT,
                EventType.CDSECT,
                EventType.IGNORABLE_WHITESPACE,
                -> frames.lastOrNull()?.text?.append(reader.text)

                EventType.DOCDECL,
                EventType.ENTITY_REF,
                -> throw CblParseException(
                    failure = CblParseFailure.UNSAFE_XML,
                    message = "DTD and entity declarations are not allowed in CBL files",
                )

                else -> Unit
            }
        }

        if (!rootSeen) {
            throw CblParseException(
                failure = CblParseFailure.INVALID_ROOT,
                message = "The CBL document does not contain a <$READING_LIST_ELEMENT> root element",
            )
        }
        if (!rootClosed || frames.isNotEmpty() || currentBook != null || currentDatabase != null) {
            throw CblParseException(
                failure = CblParseFailure.MALFORMED_XML,
                message = "The CBL document ended before all elements were closed",
            )
        }

        if (name == null) {
            warnings += CblParseWarning(
                code = CblParseWarningCode.MISSING_NAME,
                message = "The reading list has no name",
            )
        }
        if (books.isEmpty()) {
            warnings += CblParseWarning(
                code = CblParseWarningCode.EMPTY_LIST,
                message = "The reading list contains no books",
            )
        }
        if (declaredIssueCount != null && declaredIssueCount != books.size) {
            warnings += CblParseWarning(
                code = CblParseWarningCode.ISSUE_COUNT_MISMATCH,
                message = "NumIssues declares $declaredIssueCount entries but ${books.size} were found",
            )
        }

        return CblReadingList(
            name = name,
            description = descriptionParts.takeIf(List<String>::isNotEmpty)?.joinToString("\n\n"),
            declaredIssueCount = declaredIssueCount,
            books = books.toList(),
            extraAttributes = rootAttributes,
            extraElements = rootExtraElements.toImmutableLists(),
            warnings = warnings.toList(),
        )
    }

    private fun validateInput(xml: String) {
        if (xml.isBlank()) {
            throw CblParseException(
                failure = CblParseFailure.EMPTY_INPUT,
                message = "The CBL document is empty",
            )
        }
        if (xml.length > limits.maxCharacters) {
            throw CblParseException(
                failure = CblParseFailure.INPUT_TOO_LARGE,
                message = "The CBL document exceeds the ${limits.maxCharacters} character limit",
            )
        }
        if (UNSAFE_DECLARATION.containsMatchIn(xml)) {
            throw CblParseException(
                failure = CblParseFailure.UNSAFE_XML,
                message = "DTD and entity declarations are not allowed in CBL files",
            )
        }
    }

    private fun invalidStructure(message: String) = CblParseException(
        failure = CblParseFailure.INVALID_STRUCTURE,
        message = message,
    )

    private data class ElementFrame(
        val name: String,
        val text: StringBuilder = StringBuilder(),
    )

    private class BookBuilder(
        val position: Int,
        val series: String?,
        val number: String?,
        val volume: String?,
        val year: String?,
        val extraAttributes: Map<String, String>,
        val databases: MutableList<CblDatabaseReference> = mutableListOf(),
        val extraElements: MutableMap<String, MutableList<String>> = linkedMapOf(),
    ) {
        fun build(): CblBook {
            val validSeries = series?.takeIf(String::isNotBlank)
                ?: throw CblParseException(
                    failure = CblParseFailure.MISSING_BOOK_ATTRIBUTE,
                    message = "Book at position $position is missing a non-blank Series attribute",
                )
            val validNumber = number?.takeIf(String::isNotBlank)
                ?: throw CblParseException(
                    failure = CblParseFailure.MISSING_BOOK_ATTRIBUTE,
                    message = "Book at position $position is missing a non-blank Number attribute",
                )
            return CblBook(
                position = position,
                series = validSeries,
                number = validNumber,
                volume = volume,
                year = year,
                databases = databases.toList(),
                extraAttributes = extraAttributes,
                extraElements = extraElements.toImmutableLists(),
            )
        }

        companion object {
            fun from(attributes: Map<String, String>, position: Int) = BookBuilder(
                position = position,
                series = attributes.valueFor(SERIES_ATTRIBUTE),
                number = attributes.valueFor(NUMBER_ATTRIBUTE),
                volume = attributes.valueFor(VOLUME_ATTRIBUTE),
                year = attributes.valueFor(YEAR_ATTRIBUTE),
                extraAttributes = attributes.withoutKnownAttributes(BOOK_ATTRIBUTES),
            )
        }
    }

    private class DatabaseBuilder(
        val name: String?,
        val seriesId: String?,
        val issueId: String?,
        val extraAttributes: Map<String, String>,
        val extraElements: MutableMap<String, MutableList<String>> = linkedMapOf(),
    ) {
        fun build() = CblDatabaseReference(
            name = name,
            seriesId = seriesId,
            issueId = issueId,
            extraAttributes = extraAttributes,
            extraElements = extraElements.toImmutableLists(),
        )

        companion object {
            fun from(attributes: Map<String, String>) = DatabaseBuilder(
                name = attributes.valueFor(NAME_ATTRIBUTE),
                seriesId = attributes.valueFor(SERIES_ATTRIBUTE),
                issueId = attributes.valueFor(ISSUE_ATTRIBUTE),
                extraAttributes = attributes.withoutKnownAttributes(DATABASE_ATTRIBUTES),
            )
        }
    }

    private companion object {
        const val READING_LIST_ELEMENT = "ReadingList"
        const val NAME_ELEMENT = "Name"
        const val DESCRIPTION_ELEMENT = "Description"
        const val NOTES_ELEMENT = "Notes"
        const val ISSUE_COUNT_ELEMENT = "NumIssues"
        const val BOOK_ELEMENT = "Book"
        const val DATABASE_ELEMENT = "Database"

        const val SERIES_ATTRIBUTE = "Series"
        const val NUMBER_ATTRIBUTE = "Number"
        const val VOLUME_ATTRIBUTE = "Volume"
        const val YEAR_ATTRIBUTE = "Year"
        const val NAME_ATTRIBUTE = "Name"
        const val ISSUE_ATTRIBUTE = "Issue"

        val KNOWN_READING_LIST_CHILDREN = setOf(
            NAME_ELEMENT,
            DESCRIPTION_ELEMENT,
            NOTES_ELEMENT,
            ISSUE_COUNT_ELEMENT,
            "Books",
        )
        val BOOK_ATTRIBUTES = setOf(SERIES_ATTRIBUTE, NUMBER_ATTRIBUTE, VOLUME_ATTRIBUTE, YEAR_ATTRIBUTE)
        val DATABASE_ATTRIBUTES = setOf(NAME_ATTRIBUTE, SERIES_ATTRIBUTE, ISSUE_ATTRIBUTE)
        val UNSAFE_DECLARATION = Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
    }
}

private fun XmlReader.readAttributes(): Map<String, String> = buildMap {
    for (index in 0 until attributeCount) {
        val prefix = getAttributePrefix(index)
        val localName = getAttributeLocalName(index)
        val qualifiedName = if (prefix.isEmpty()) localName else "$prefix:$localName"
        put(qualifiedName, getAttributeValue(index))
    }
}

private fun Map<String, String>.valueFor(localName: String): String? =
    entries.firstOrNull { it.key.substringAfterLast(':').equals(localName, ignoreCase = true) }?.value

private fun Map<String, String>.withoutKnownAttributes(knownNames: Set<String>): Map<String, String> =
    filterKeys { key -> knownNames.none { it.equals(key.substringAfterLast(':'), ignoreCase = true) } }

private fun MutableMap<String, MutableList<String>>.addValue(name: String, value: String) {
    getOrPut(name) { mutableListOf() } += value
}

private fun Map<String, List<String>>.toImmutableLists(): Map<String, List<String>> =
    mapValues { (_, values) -> values.toList() }
