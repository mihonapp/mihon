package tachiyomi.data.readinglist

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.readinglist.cbl.model.CblDatabaseReference
import tachiyomi.domain.readinglist.cbl.model.CblReadingList
import tachiyomi.domain.readinglist.model.ReadingList
import tachiyomi.domain.readinglist.model.ReadingListEntry
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListSummary
import tachiyomi.domain.readinglist.repository.ReadingListRepository

class ReadingListRepositoryImpl(
    private val database: Database,
    json: Json,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() },
) : ReadingListRepository {

    private val codec = ReadingListStorageCodec(json)

    override suspend fun get(id: Long): ReadingList? {
        val readingList = database.reading_listsQueries
            .getReadingList(id, ::mapReadingListRow)
            .awaitAsOneOrNull()
            ?: return null

        val entries = database.reading_listsQueries
            .getReadingListEntries(id, ::mapEntryRow)
            .awaitAsList()
        val databaseReferences = database.reading_listsQueries
            .getDatabaseReferencesByReadingListId(id, ::mapDatabaseReferenceRow)
            .awaitAsList()
            .groupBy(DatabaseReferenceRow::entryId)

        return ReadingList(
            id = readingList.id,
            name = readingList.name,
            description = readingList.description,
            declaredIssueCount = readingList.declaredIssueCount?.toInt(),
            entries = entries.map { entry ->
                ReadingListEntry(
                    id = entry.id,
                    readingListId = entry.readingListId,
                    position = entry.position.toInt(),
                    series = entry.series,
                    number = entry.number,
                    volume = entry.volume,
                    year = entry.year,
                    databases = databaseReferences[entry.id].orEmpty().map { reference ->
                        CblDatabaseReference(
                            name = reference.name,
                            seriesId = reference.seriesId,
                            issueId = reference.issueId,
                            extraAttributes = codec.decodeAttributes(reference.extraAttributes),
                            extraElements = codec.decodeElements(reference.extraElements),
                        )
                    },
                    extraAttributes = codec.decodeAttributes(entry.extraAttributes),
                    extraElements = codec.decodeElements(entry.extraElements),
                    resolutionState = ReadingListEntryResolutionState.valueOf(entry.resolutionState),
                    matchedSourceId = entry.matchedSourceId,
                    matchedMangaUrl = entry.matchedMangaUrl,
                    matchedChapterUrl = entry.matchedChapterUrl,
                    confidence = entry.confidence,
                    matcherVersion = entry.matcherVersion,
                    userConfirmed = entry.userConfirmed,
                    skipped = entry.skipped,
                )
            },
            extraAttributes = codec.decodeAttributes(readingList.extraAttributes),
            extraElements = codec.decodeElements(readingList.extraElements),
            warnings = codec.decodeWarnings(readingList.warnings),
            currentPosition = readingList.currentPosition?.toInt(),
            createdAt = readingList.createdAt,
            updatedAt = readingList.updatedAt,
        )
    }

    override suspend fun getAll(): List<ReadingListSummary> {
        return database.reading_listsQueries
            .getReadingLists(::mapSummary)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<ReadingListSummary>> {
        return database.reading_listsQueries
            .getReadingLists(::mapSummary)
            .subscribeToList()
    }

    override suspend fun insert(readingList: CblReadingList): Long {
        readingList.requireValidPersistenceOrder()
        val timestamp = currentTimeMillis()

        return database.transactionWithResult {
            database.reading_listsQueries.insertReadingList(
                name = readingList.name,
                description = readingList.description,
                declaredIssueCount = readingList.declaredIssueCount?.toLong(),
                extraAttributes = codec.encodeAttributes(readingList.extraAttributes),
                extraElements = codec.encodeElements(readingList.extraElements),
                warnings = codec.encodeWarnings(readingList.warnings),
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            val readingListId = database.reading_listsQueries.lastInsertRowId().awaitAsOne()

            readingList.books.forEach { book ->
                database.reading_listsQueries.insertReadingListEntry(
                    readingListId = readingListId,
                    position = book.position.toLong(),
                    series = book.series,
                    number = book.number,
                    volume = book.volume,
                    year = book.year,
                    extraAttributes = codec.encodeAttributes(book.extraAttributes),
                    extraElements = codec.encodeElements(book.extraElements),
                    resolutionState = ReadingListEntryResolutionState.UNSEARCHED.name,
                )
                val entryId = database.reading_listsQueries.lastInsertRowId().awaitAsOne()

                book.databases.forEachIndexed { position, reference ->
                    database.reading_listsQueries.insertDatabaseReference(
                        entryId = entryId,
                        position = position.toLong(),
                        name = reference.name,
                        seriesId = reference.seriesId,
                        issueId = reference.issueId,
                        extraAttributes = codec.encodeAttributes(reference.extraAttributes),
                        extraElements = codec.encodeElements(reference.extraElements),
                    )
                }
            }

            readingListId
        }
    }

    override suspend fun updateProgress(id: Long, currentPosition: Int?): Boolean {
        require(currentPosition == null || currentPosition >= 0) {
            "Reading-list position cannot be negative"
        }

        return database.transactionWithResult {
            if (!database.reading_listsQueries.readingListExists(id).awaitAsOne()) {
                return@transactionWithResult false
            }
            if (
                currentPosition != null &&
                !database.reading_listsQueries.entryExistsAtPosition(id, currentPosition.toLong()).awaitAsOne()
            ) {
                return@transactionWithResult false
            }

            database.reading_listsQueries.updateProgress(
                currentPosition = currentPosition?.toLong(),
                updatedAt = currentTimeMillis(),
                id = id,
            )
            true
        }
    }

    override suspend fun delete(id: Long) {
        database.reading_listsQueries.deleteReadingList(id)
    }

    private fun mapReadingListRow(
        id: Long,
        name: String?,
        description: String?,
        declaredIssueCount: Long?,
        extraAttributes: String,
        extraElements: String,
        warnings: String,
        currentPosition: Long?,
        createdAt: Long,
        updatedAt: Long,
    ): ReadingListRow {
        return ReadingListRow(
            id = id,
            name = name,
            description = description,
            declaredIssueCount = declaredIssueCount,
            extraAttributes = extraAttributes,
            extraElements = extraElements,
            warnings = warnings,
            currentPosition = currentPosition,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun mapSummary(
        id: Long,
        name: String?,
        entryCount: Long,
        currentPosition: Long?,
        createdAt: Long,
        updatedAt: Long,
    ): ReadingListSummary {
        return ReadingListSummary(
            id = id,
            name = name,
            entryCount = entryCount.toInt(),
            currentPosition = currentPosition?.toInt(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun mapEntryRow(
        id: Long,
        readingListId: Long,
        position: Long,
        series: String,
        number: String,
        volume: String?,
        year: String?,
        extraAttributes: String,
        extraElements: String,
        resolutionState: String,
        matchedSourceId: Long?,
        matchedMangaUrl: String?,
        matchedChapterUrl: String?,
        confidence: Double?,
        matcherVersion: Long?,
        userConfirmed: Boolean,
        skipped: Boolean,
    ): EntryRow {
        return EntryRow(
            id = id,
            readingListId = readingListId,
            position = position,
            series = series,
            number = number,
            volume = volume,
            year = year,
            extraAttributes = extraAttributes,
            extraElements = extraElements,
            resolutionState = resolutionState,
            matchedSourceId = matchedSourceId,
            matchedMangaUrl = matchedMangaUrl,
            matchedChapterUrl = matchedChapterUrl,
            confidence = confidence,
            matcherVersion = matcherVersion,
            userConfirmed = userConfirmed,
            skipped = skipped,
        )
    }

    private fun mapDatabaseReferenceRow(
        entryId: Long,
        position: Long,
        name: String?,
        seriesId: String?,
        issueId: String?,
        extraAttributes: String,
        extraElements: String,
    ): DatabaseReferenceRow {
        return DatabaseReferenceRow(
            entryId = entryId,
            position = position,
            name = name,
            seriesId = seriesId,
            issueId = issueId,
            extraAttributes = extraAttributes,
            extraElements = extraElements,
        )
    }

    private data class ReadingListRow(
        val id: Long,
        val name: String?,
        val description: String?,
        val declaredIssueCount: Long?,
        val extraAttributes: String,
        val extraElements: String,
        val warnings: String,
        val currentPosition: Long?,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class EntryRow(
        val id: Long,
        val readingListId: Long,
        val position: Long,
        val series: String,
        val number: String,
        val volume: String?,
        val year: String?,
        val extraAttributes: String,
        val extraElements: String,
        val resolutionState: String,
        val matchedSourceId: Long?,
        val matchedMangaUrl: String?,
        val matchedChapterUrl: String?,
        val confidence: Double?,
        val matcherVersion: Long?,
        val userConfirmed: Boolean,
        val skipped: Boolean,
    )

    private data class DatabaseReferenceRow(
        val entryId: Long,
        val position: Long,
        val name: String?,
        val seriesId: String?,
        val issueId: String?,
        val extraAttributes: String,
        val extraElements: String,
    )
}
