package eu.kanade.tachiyomi.ui.readinglist

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.ReadingListSeriesKey
import tachiyomi.domain.readinglist.model.ReadingList
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListCandidateRejection
import tachiyomi.domain.readinglist.model.ReadingListEntry
import tachiyomi.domain.readinglist.model.ReadingListEntryOverride
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult
import tachiyomi.domain.readinglist.model.ReadingListResolutionData
import tachiyomi.domain.readinglist.model.ReadingListSeriesMapping
import tachiyomi.domain.readinglist.repository.ReadingListRepository
import tachiyomi.domain.readinglist.repository.ReadingListResolutionRepository
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

@Execution(ExecutionMode.CONCURRENT)
class ReadingListCandidateSearchTest {

    @Test
    fun `searches one selected source once for entries in the same series`() = runTest {
        val selectedSource = FakeHttpSource(
            sourceName = "Selected",
            mangas = listOf(manga("/series/example", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/example" to listOf(
                    chapter("/chapter/1", "Example #1", 1.0f),
                    chapter("/chapter/2", "Example #2", 2.0f),
                ),
            ),
        )
        val unselectedSource = FakeHttpSource(
            sourceName = "Unselected",
            mangas = listOf(manga("/series/other", "Example")),
            chaptersByMangaUrl = emptyMap(),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(selectedSource.id),
            entries = listOf(
                entry(id = 10, position = 0, number = "1"),
                entry(id = 11, position = 1, number = "2"),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(selectedSource, unselectedSource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        selectedSource.searchCalls shouldBe 1
        selectedSource.chapterFetchCalls shouldBe 1
        unselectedSource.searchCalls shouldBe 0
        unselectedSource.chapterFetchCalls shouldBe 0
        writes.map(PersistedWrite::entryId) shouldContainExactly listOf(10L, 11L)
        writes.map { write -> write.update.state } shouldContainExactly listOf(
            ReadingListEntryResolutionState.AUTO_MATCHED,
            ReadingListEntryResolutionState.AUTO_MATCHED,
        )
        result.summary.autoMatchedEntries shouldBe 2
        result.summary.candidateCount shouldBe 2
    }

    @Test
    fun `marks eligible entries unavailable when no selected source is installed`() = runTest {
        val readingList = readingList(
            selectedSourceIds = listOf(99),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        writes.single().candidates shouldBe emptyList()
        writes.single().update.state shouldBe ReadingListEntryResolutionState.SOURCE_UNAVAILABLE
        result.summary.sourceUnavailableEntries shouldBe 1
        result.summary.unavailableSelectedSourceCount shouldBe 1
        result.summary.allSelectedSourcesUnavailable shouldBe true
    }

    @Test
    fun `confirmed and skipped entries are not searched or replaced`() = runTest {
        val source = FakeHttpSource(
            sourceName = "Selected",
            mangas = listOf(manga("/series/example", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/example" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(source.id),
            entries = listOf(
                entry(
                    id = 10,
                    position = 0,
                    number = "1",
                    userConfirmed = true,
                ),
                entry(
                    id = 11,
                    position = 1,
                    number = "1",
                    skipped = true,
                ),
                entry(id = 12, position = 2, number = "1"),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(source),
            writes = writes,
        )

        search.search(readingList.id)

        writes.map(PersistedWrite::entryId) shouldContainExactly listOf(12L)
    }

    @Test
    fun `one failing source does not discard another source result`() = runTest {
        val failingSource = FakeHttpSource(
            sourceName = "Outdated",
            mangas = emptyList(),
            chaptersByMangaUrl = emptyMap(),
            searchFailure = IllegalStateException(
                "Extension request failed",
                NullPointerException("fixture"),
            ),
        )
        val healthySource = FakeHttpSource(
            sourceName = "Healthy",
            mangas = listOf(manga("/series/healthy", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/healthy" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(failingSource.id, healthySource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(failingSource, healthySource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        writes.single().update.state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
        result.summary.autoMatchedEntries shouldBe 1
        result.summary.failedSourceCount shouldBe 1
        result.summary.updateRecommendedSourceCount shouldBe 1
    }

    @Test
    fun `confirmed series mapping avoids replacement series searches`() = runTest {
        val mappedSource = FakeHttpSource(
            sourceName = "Mapped",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/mapped" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val otherSource = FakeHttpSource(
            sourceName = "Other",
            mangas = listOf(manga("/series/other", "Example")),
            chaptersByMangaUrl = emptyMap(),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(mappedSource.id, otherSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            seriesMappings = listOf(
                ReadingListSeriesMapping(
                    readingListId = readingList.id,
                    seriesKey = "example",
                    seriesTitle = "Example",
                    sourceId = mappedSource.id,
                    mangaUrl = "/series/mapped",
                    userConfirmed = true,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(mappedSource, otherSource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        mappedSource.searchCalls shouldBe 0
        otherSource.searchCalls shouldBe 0
        mappedSource.chapterFetchCalls shouldBe 1
        writes.single().update.state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
        result.summary.autoMatchedEntries shouldBe 1
    }

    @Test
    fun `queued requests receive their full timeout after acquiring the permit`() = runTest {
        val tracker = RequestTracker()
        val firstSource = FakeHttpSource(
            sourceName = "First",
            mangas = listOf(manga("/series/first", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/first" to listOf(chapter("/chapter/first", "Example #1", 1.0f)),
            ),
            tracker = tracker,
            requestDelayMillis = 75,
        )
        val secondSource = FakeHttpSource(
            sourceName = "Second",
            mangas = listOf(manga("/series/second", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/second" to listOf(chapter("/chapter/second", "Example #1", 1.0f)),
            ),
            tracker = tracker,
            requestDelayMillis = 75,
        )
        val readingList = readingList(
            selectedSourceIds = listOf(firstSource.id, secondSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(firstSource, secondSource),
            writes = mutableListOf(),
            config = ReadingListCandidateSearchConfig(
                maxConcurrentRequests = 1,
                requestTimeoutMillis = 100,
            ),
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        tracker.maximumActiveRequests shouldBe 1
        firstSource.chapterFetchCalls shouldBe 1
        secondSource.chapterFetchCalls shouldBe 1
        result.summary.failedSourceCount shouldBe 0
        result.summary.reviewEntries shouldBe 1
    }

    @Test
    fun `rejected candidates are not returned to automatic matching`() = runTest {
        val source = FakeHttpSource(
            sourceName = "Selected",
            mangas = listOf(manga("/series/example", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/example" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(source.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val rejectedIdentity = ReadingListCandidateIdentity(
            sourceId = source.id,
            candidateId = stableCandidateId(
                mangaUrl = "/series/example",
                chapterUrl = "/chapter/1",
            ),
        )
        val resolution = emptyResolution(readingList.id).copy(
            rejections = listOf(
                ReadingListCandidateRejection(
                    entryId = 10,
                    identity = rejectedIdentity,
                    mangaUrl = "/series/example",
                    chapterUrl = "/chapter/1",
                    rejectedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(source),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        writes.single().candidates.map { candidate ->
            candidate.identity
        } shouldContainExactly listOf(rejectedIdentity)
        writes.single().update.state shouldBe ReadingListEntryResolutionState.UNRESOLVED
        result.summary.autoMatchedEntries shouldBe 0
        result.summary.unresolvedEntries shouldBe 1
        result.summary.candidateCount shouldBe 1
    }

    @Test
    fun `unavailable confirmed mapping does not search replacement sources`() = runTest {
        val availableSource = FakeHttpSource(
            sourceName = "Available",
            mangas = listOf(manga("/series/replacement", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/replacement" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(99, availableSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            seriesMappings = listOf(
                ReadingListSeriesMapping(
                    readingListId = readingList.id,
                    seriesKey = ReadingListSeriesKey.from("Example"),
                    seriesTitle = "Example",
                    sourceId = 99,
                    mangaUrl = "/series/confirmed",
                    userConfirmed = true,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(availableSource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        availableSource.searchCalls shouldBe 0
        availableSource.chapterFetchCalls shouldBe 0
        writes.single().candidates shouldBe emptyList()
        writes.single().update.state shouldBe ReadingListEntryResolutionState.SOURCE_UNAVAILABLE
        result.summary.sourceUnavailableEntries shouldBe 1
        result.summary.allSelectedSourcesUnavailable shouldBe false
    }

    @Test
    fun `confirmed series history requires the same manga`() = runTest {
        val source = FakeHttpSource(
            sourceName = "Selected",
            mangas = listOf(manga("/series/different", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/different" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(source.id),
            entries = listOf(
                entry(
                    id = 9,
                    position = 0,
                    number = "0",
                    userConfirmed = true,
                    matchedSourceId = source.id,
                    matchedMangaUrl = "/series/confirmed",
                ),
                entry(id = 10, position = 1, number = "1"),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(source),
            writes = writes,
        )

        search.search(readingList.id)

        writes.single().candidates.single().breakdown.confirmedHistory shouldBe
            ConfirmedHistoryEvidence.SOURCE
    }

    @Test
    fun `entry override seed is eligible only for its owning entry`() = runTest {
        val mappedSource = FakeHttpSource(
            sourceName = "Mapped scope",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/mapped-scope" to listOf(chapter("/chapter/mapped-2", "Example #2", 2.0f)),
            ),
        )
        val overrideSource = FakeHttpSource(
            sourceName = "Override scope",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/override-scope" to listOf(chapter("/chapter/override-1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(mappedSource.id, overrideSource.id),
            entries = listOf(
                entry(id = 10, position = 0, number = "1"),
                entry(id = 11, position = 1, number = "2"),
            ),
        )
        val resolution = emptyResolution(readingList.id).copy(
            entryOverrides = listOf(
                ReadingListEntryOverride(10, overrideSource.id, "/series/override-scope", null, 1, 1),
            ),
            seriesMappings = listOf(
                ReadingListSeriesMapping(
                    readingList.id,
                    ReadingListSeriesKey.from("Example"),
                    "Example",
                    mappedSource.id,
                    "/series/mapped-scope",
                    true,
                    1,
                    1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList,
            resolution,
            FakeSourceManager(mappedSource, overrideSource),
            writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        writes.map { write -> write.entryId to write.candidates.map { it.mangaUrl } } shouldContainExactly
            listOf(
                10L to listOf("/series/override-scope"),
                11L to listOf("/series/mapped-scope"),
            )
        result.summary.autoMatchedEntries shouldBe 2
    }

    @Test
    fun `entry override takes precedence over a confirmed series mapping`() = runTest {
        val mappedSource = FakeHttpSource(
            sourceName = "Mapped",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/mapped" to listOf(chapter("/chapter/mapped", "Example #1", 1.0f)),
            ),
        )
        val overrideSource = FakeHttpSource(
            sourceName = "Override",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/override" to listOf(chapter("/chapter/override", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(mappedSource.id, overrideSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            entryOverrides = listOf(
                ReadingListEntryOverride(
                    entryId = 10,
                    sourceId = overrideSource.id,
                    mangaUrl = "/series/override",
                    chapterUrl = null,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
            seriesMappings = listOf(
                ReadingListSeriesMapping(
                    readingListId = readingList.id,
                    seriesKey = ReadingListSeriesKey.from("Example"),
                    seriesTitle = "Example",
                    sourceId = mappedSource.id,
                    mangaUrl = "/series/mapped",
                    userConfirmed = true,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(mappedSource, overrideSource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        mappedSource.searchCalls shouldBe 0
        mappedSource.chapterFetchCalls shouldBe 0
        overrideSource.searchCalls shouldBe 0
        overrideSource.chapterFetchCalls shouldBe 1
        writes.single().candidates.single().identity.sourceId shouldBe overrideSource.id
        writes.single().update.state shouldBe ReadingListEntryResolutionState.AUTO_MATCHED
        result.summary.autoMatchedEntries shouldBe 1
    }

    @Test
    fun `source-only entry override limits the series search`() = runTest {
        val lowerPrioritySource = FakeHttpSource(
            sourceName = "Lower priority",
            mangas = listOf(manga("/series/lower", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/lower" to listOf(chapter("/chapter/lower", "Example #1", 1.0f)),
            ),
        )
        val overrideSource = FakeHttpSource(
            sourceName = "Override",
            mangas = listOf(manga("/series/override", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/override" to listOf(chapter("/chapter/override", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(lowerPrioritySource.id, overrideSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            entryOverrides = listOf(
                ReadingListEntryOverride(
                    entryId = 10,
                    sourceId = overrideSource.id,
                    mangaUrl = null,
                    chapterUrl = null,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(lowerPrioritySource, overrideSource),
            writes = writes,
        )

        search.search(readingList.id)

        lowerPrioritySource.searchCalls shouldBe 0
        lowerPrioritySource.chapterFetchCalls shouldBe 0
        overrideSource.searchCalls shouldBe 1
        overrideSource.chapterFetchCalls shouldBe 1
        writes.single().candidates.single().identity.sourceId shouldBe overrideSource.id
    }

    @Test
    fun `unavailable entry override is not bypassed by lower priority sources`() = runTest {
        val availableSource = FakeHttpSource(
            sourceName = "Available",
            mangas = listOf(manga("/series/replacement", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/replacement" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(availableSource.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            entryOverrides = listOf(
                ReadingListEntryOverride(
                    entryId = 10,
                    sourceId = 99,
                    mangaUrl = null,
                    chapterUrl = null,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(availableSource),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        availableSource.searchCalls shouldBe 0
        availableSource.chapterFetchCalls shouldBe 0
        writes.single().update.state shouldBe ReadingListEntryResolutionState.SOURCE_UNAVAILABLE
        result.summary.sourceUnavailableEntries shouldBe 1
        result.summary.allSelectedSourcesUnavailable shouldBe false
    }

    @Test
    fun `missing chapter override is not converted into a synthetic match`() = runTest {
        val source = FakeHttpSource(
            sourceName = "Selected",
            mangas = emptyList(),
            chaptersByMangaUrl = mapOf(
                "/series/example" to listOf(chapter("/chapter/other", "Example #1", 1.0f)),
            ),
        )
        val readingList = readingList(
            selectedSourceIds = listOf(source.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val resolution = emptyResolution(readingList.id).copy(
            entryOverrides = listOf(
                ReadingListEntryOverride(
                    entryId = 10,
                    sourceId = source.id,
                    mangaUrl = "/series/example",
                    chapterUrl = "/chapter/missing",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = resolution,
            sourceManager = FakeSourceManager(source),
            writes = writes,
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        source.chapterFetchCalls shouldBe 1
        writes.single().candidates shouldBe emptyList()
        writes.single().update.state shouldBe ReadingListEntryResolutionState.UNRESOLVED
        result.summary.unresolvedEntries shouldBe 1
    }

    @Test
    fun `source requests time out without blocking the whole search`() = runTest {
        val source = FakeHttpSource(
            sourceName = "Slow",
            mangas = listOf(manga("/series/example", "Example")),
            chaptersByMangaUrl = emptyMap(),
            requestDelayMillis = 100,
        )
        val readingList = readingList(
            selectedSourceIds = listOf(source.id),
            entries = listOf(entry(id = 10, position = 0, number = "1")),
        )
        val writes = mutableListOf<PersistedWrite>()
        val search = search(
            readingList = readingList,
            resolution = emptyResolution(readingList.id),
            sourceManager = FakeSourceManager(source),
            writes = writes,
            config = ReadingListCandidateSearchConfig(
                requestTimeoutMillis = 10,
            ),
        )

        val result = search.search(readingList.id) as ReadingListCandidateSearchResult.Completed

        writes.single().update.state shouldBe ReadingListEntryResolutionState.UNRESOLVED
        result.summary.unresolvedEntries shouldBe 1
        result.summary.failedSourceCount shouldBe 1
    }

    @Test
    fun `simultaneous list searches share the configured request limit`() = runTest {
        val tracker = RequestTracker()
        val source = FakeHttpSource(
            sourceName = "Selected",
            mangas = listOf(manga("/series/example", "Example")),
            chaptersByMangaUrl = mapOf(
                "/series/example" to listOf(chapter("/chapter/1", "Example #1", 1.0f)),
            ),
            tracker = tracker,
            requestDelayMillis = 10,
        )
        val readingLists = listOf(1L, 2L).associateWith { readingListId ->
            readingList(
                id = readingListId,
                selectedSourceIds = listOf(source.id),
                entries = listOf(
                    entry(
                        id = readingListId * 10,
                        readingListId = readingListId,
                        position = 0,
                        number = "1",
                    ),
                ),
            )
        }
        val readingListRepository = mockk<ReadingListRepository>()
        val resolutionRepository = mockk<ReadingListResolutionRepository>()
        coEvery { readingListRepository.get(any()) } answers {
            readingLists[firstArg()]
        }
        coEvery { resolutionRepository.get(any()) } answers {
            emptyResolution(firstArg())
        }
        coEvery {
            resolutionRepository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = any(),
                candidates = any(),
                update = any(),
            )
        } returns ReadingListProtectedWriteResult.APPLIED
        val search = ReadingListCandidateSearch(
            readingListRepository = readingListRepository,
            resolutionRepository = resolutionRepository,
            sourceManager = FakeSourceManager(source),
            config = ReadingListCandidateSearchConfig(
                maxConcurrentRequests = 1,
            ),
        )

        coroutineScope {
            readingLists.keys.map { readingListId ->
                async { search.search(readingListId) }
            }.awaitAll()
        }

        tracker.maximumActiveRequests shouldBe 1
    }

    private fun search(
        readingList: ReadingList,
        resolution: ReadingListResolutionData,
        sourceManager: SourceManager,
        writes: MutableList<PersistedWrite>,
        config: ReadingListCandidateSearchConfig = ReadingListCandidateSearchConfig(),
    ): ReadingListCandidateSearch {
        val readingListRepository = mockk<ReadingListRepository>()
        val resolutionRepository = mockk<ReadingListResolutionRepository>()

        coEvery { readingListRepository.get(readingList.id) } returns readingList
        coEvery { resolutionRepository.get(readingList.id) } returns resolution
        coEvery {
            resolutionRepository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = any(),
                candidates = any(),
                update = any(),
            )
        } coAnswers {
            writes += PersistedWrite(
                entryId = firstArg(),
                candidates = secondArg(),
                update = thirdArg(),
            )
            ReadingListProtectedWriteResult.APPLIED
        }

        return ReadingListCandidateSearch(
            readingListRepository = readingListRepository,
            resolutionRepository = resolutionRepository,
            sourceManager = sourceManager,
            config = config,
        )
    }

    private fun readingList(
        selectedSourceIds: List<Long>,
        entries: List<ReadingListEntry>,
        id: Long = 1,
    ): ReadingList {
        return ReadingList(
            id = id,
            name = "Fixture",
            description = null,
            declaredIssueCount = entries.size,
            entries = entries,
            selectedSourceIds = selectedSourceIds,
            extraAttributes = emptyMap(),
            extraElements = emptyMap(),
            warnings = emptyList(),
            currentPosition = null,
            createdAt = 1,
            updatedAt = 1,
        )
    }

    private fun entry(
        id: Long,
        position: Int,
        number: String,
        readingListId: Long = 1,
        series: String = "Example",
        volume: String? = null,
        year: String? = null,
        userConfirmed: Boolean = false,
        skipped: Boolean = false,
        matchedSourceId: Long? = null,
        matchedMangaUrl: String? = null,
    ): ReadingListEntry {
        return ReadingListEntry(
            id = id,
            readingListId = readingListId,
            position = position,
            series = series,
            number = number,
            volume = volume,
            year = year,
            databases = emptyList(),
            extraAttributes = emptyMap(),
            extraElements = emptyMap(),
            resolutionState = if (userConfirmed) {
                ReadingListEntryResolutionState.USER_CONFIRMED
            } else {
                ReadingListEntryResolutionState.UNSEARCHED
            },
            matchedSourceId = matchedSourceId,
            matchedMangaUrl = matchedMangaUrl,
            matchedChapterUrl = null,
            confidence = null,
            matcherVersion = null,
            userConfirmed = userConfirmed,
            skipped = skipped,
        )
    }

    private fun emptyResolution(readingListId: Long): ReadingListResolutionData {
        return ReadingListResolutionData(
            readingListId = readingListId,
            candidates = emptyList(),
            rejections = emptyList(),
            entryOverrides = emptyList(),
            seriesMappings = emptyList(),
        )
    }

    private data class PersistedWrite(
        val entryId: Long,
        val candidates: List<tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot>,
        val update: tachiyomi.domain.readinglist.model.ReadingListAutomaticResolutionUpdate,
    )

    private class FakeSourceManager(
        vararg sources: Source,
    ) : SourceManager {

        private val sourceMap = sources.associateBy(Source::id)

        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val sources: Flow<List<Source>> = MutableStateFlow(sourceMap.values.toList())

        override fun get(sourceKey: Long): Source? = sourceMap[sourceKey]

        override fun getOrStub(sourceKey: Long): Source {
            return sourceMap[sourceKey] ?: StubSource(
                id = sourceKey,
                lang = "",
                name = "",
            )
        }

        override fun getAll(): List<Source> = sourceMap.values.toList()

        override fun getOnlineSources(): List<HttpSource> = sourceMap.values.filterIsInstance<HttpSource>()

        override fun getStubSources(): List<StubSource> = emptyList()
    }

    private class FakeHttpSource(
        sourceName: String,
        private val mangas: List<SManga>,
        private val chaptersByMangaUrl: Map<String, List<SChapter>>,
        private val tracker: RequestTracker? = null,
        private val searchFailure: Exception? = null,
        private val requestDelayMillis: Long = 0,
    ) : HttpSource() {

        override val name: String = sourceName
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        override val baseUrl: String = "https://example.invalid"

        var searchCalls: Int = 0
            private set
        var chapterFetchCalls: Int = 0
            private set

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage {
            searchCalls += 1
            return tracked {
                searchFailure?.let { error -> throw error }
                MangasPage(
                    mangas = mangas,
                    hasNextPage = false,
                )
            }
        }

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            chapterFetchCalls += 1
            return tracked {
                SMangaUpdate(
                    manga = manga,
                    chapters = chaptersByMangaUrl[manga.url].orEmpty(),
                )
            }
        }

        private suspend fun <T> tracked(block: () -> T): T {
            tracker?.started()
            return try {
                if (requestDelayMillis > 0) {
                    delay(requestDelayMillis)
                }
                block()
            } finally {
                tracker?.finished()
            }
        }
    }

    private class RequestTracker {
        private var activeRequests: Int = 0
        var maximumActiveRequests: Int = 0
            private set

        @Synchronized
        fun started() {
            activeRequests += 1
            maximumActiveRequests = maxOf(maximumActiveRequests, activeRequests)
        }

        @Synchronized
        fun finished() {
            activeRequests -= 1
        }
    }

    private fun stableCandidateId(
        mangaUrl: String,
        chapterUrl: String,
    ): String {
        val input = "$mangaUrl\u0000$chapterUrl"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private companion object {
        fun manga(
            url: String,
            title: String,
        ): SManga {
            return SManga.create().apply {
                this.url = url
                this.title = title
            }
        }

        fun chapter(
            url: String,
            name: String,
            number: Float,
        ): SChapter {
            return SChapter.create().apply {
                this.url = url
                this.name = name
                this.chapter_number = number
            }
        }
    }
}
