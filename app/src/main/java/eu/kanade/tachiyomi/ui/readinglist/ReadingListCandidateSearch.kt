package eu.kanade.tachiyomi.ui.readinglist

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.readinglist.matching.ConfirmedHistoryEvidence
import tachiyomi.domain.readinglist.matching.EvidenceAgreement
import tachiyomi.domain.readinglist.matching.ReadingListChapterIssueExtractor
import tachiyomi.domain.readinglist.matching.ReadingListMatchCandidate
import tachiyomi.domain.readinglist.matching.ReadingListMatchDecision
import tachiyomi.domain.readinglist.matching.ReadingListMatchQuery
import tachiyomi.domain.readinglist.matching.ReadingListMatchScorer
import tachiyomi.domain.readinglist.matching.ReadingListSeriesKey
import tachiyomi.domain.readinglist.matching.ScoredReadingListMatchCandidate
import tachiyomi.domain.readinglist.matching.SourcePreferenceLevel
import tachiyomi.domain.readinglist.model.ReadingList
import tachiyomi.domain.readinglist.model.ReadingListAutomaticResolutionUpdate
import tachiyomi.domain.readinglist.model.ReadingListCandidateIdentity
import tachiyomi.domain.readinglist.model.ReadingListEntry
import tachiyomi.domain.readinglist.model.ReadingListEntryOverride
import tachiyomi.domain.readinglist.model.ReadingListEntryResolutionState
import tachiyomi.domain.readinglist.model.ReadingListMatchCandidateSnapshot
import tachiyomi.domain.readinglist.model.ReadingListProtectedWriteResult
import tachiyomi.domain.readinglist.model.ReadingListResolutionData
import tachiyomi.domain.readinglist.model.ReadingListSeriesMapping
import tachiyomi.domain.readinglist.normalization.IssueNumberNormalizer
import tachiyomi.domain.readinglist.normalization.NormalizedIssueNumber
import tachiyomi.domain.readinglist.normalization.TitleNormalizer
import tachiyomi.domain.readinglist.repository.ReadingListRepository
import tachiyomi.domain.readinglist.repository.ReadingListResolutionRepository
import tachiyomi.domain.source.service.SourceManager
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ReadingListCandidateSearch(
    private val readingListRepository: ReadingListRepository,
    private val resolutionRepository: ReadingListResolutionRepository,
    private val sourceManager: SourceManager,
    private val scorer: ReadingListMatchScorer = ReadingListMatchScorer(),
    private val config: ReadingListCandidateSearchConfig = ReadingListCandidateSearchConfig(),
) {

    private val requestSemaphore = Semaphore(config.maxConcurrentRequests)

    suspend fun search(readingListId: Long): ReadingListCandidateSearchResult = coroutineScope {
        sourceManager.isInitialized.first { initialized -> initialized }

        val readingList = readingListRepository.get(readingListId)
            ?: return@coroutineScope ReadingListCandidateSearchResult.ReadingListNotFound
        val resolution = resolutionRepository.get(readingListId)
            ?: return@coroutineScope ReadingListCandidateSearchResult.ReadingListNotFound

        val selectedSources = readingList.selectedSourceIds.mapIndexedNotNull { order, sourceId ->
            (sourceManager.get(sourceId) as? HttpSource)?.let { source ->
                SelectedSource(
                    source = source,
                    order = order,
                )
            }
        }
        val unavailableSelectedSourceCount = readingList.selectedSourceIds.size - selectedSources.size
        val eligibleEntries = readingList.entries.filter { entry ->
            !entry.userConfirmed && !entry.skipped
        }
        val accumulator = SearchAccumulator(
            selectedSourceCount = readingList.selectedSourceIds.size,
            unavailableSelectedSourceCount = unavailableSelectedSourceCount,
        )

        if (eligibleEntries.isEmpty()) {
            return@coroutineScope ReadingListCandidateSearchResult.Completed(accumulator.toSummary())
        }

        if (selectedSources.isEmpty()) {
            eligibleEntries.forEach { entry ->
                persistSourceUnavailable(entry, accumulator)
            }
            return@coroutineScope ReadingListCandidateSearchResult.Completed(accumulator.toSummary())
        }

        val operation = SearchOperation(
            readingList = readingList,
            resolution = resolution,
            selectedSources = selectedSources,
            requestSemaphore = requestSemaphore,
        )

        eligibleEntries
            .groupBy { entry ->
                ReadingListSeriesKey.from(
                    seriesTitle = entry.series,
                    volume = entry.volume,
                    year = entry.year,
                )
            }
            .entries
            .sortedBy { (_, entries) -> entries.minOf(ReadingListEntry::position) }
            .forEach { (seriesKey, entries) ->
                searchSeriesGroup(
                    seriesKey = seriesKey,
                    entries = entries,
                    operation = operation,
                    accumulator = accumulator,
                )
            }

        accumulator.failedSourceCount = operation.failedSourceIds.size
        accumulator.updateRecommendedSourceCount = operation.updateRecommendedSourceIds.size
        ReadingListCandidateSearchResult.Completed(accumulator.toSummary())
    }

    private suspend fun searchSeriesGroup(
        seriesKey: String,
        entries: List<ReadingListEntry>,
        operation: SearchOperation,
        accumulator: SearchAccumulator,
    ) = coroutineScope {
        val representativeEntry = entries.minByOrNull(ReadingListEntry::position)
            ?: return@coroutineScope
        val mapping = operation.resolution.seriesMappings
            .firstOrNull { storedMapping -> storedMapping.seriesKey == seriesKey }
        val confirmedMapping = mapping
            ?.takeIf(ReadingListSeriesMapping::userConfirmed)
        val entriesWithoutOverride = entries.filter { entry ->
            entry.id !in operation.entryOverrideById
        }
        val eligibleEntryIdsBySeries = mutableMapOf<RemoteSeriesIdentity, MutableSet<Long>>()
        fun allow(identity: RemoteSeriesIdentity, entryIds: Iterable<Long>) {
            eligibleEntryIdsBySeries.getOrPut(identity, { mutableSetOf() }).addAll(entryIds)
        }

        val directSeeds = buildDirectSeeds(
            entries = entries,
            mapping = mapping,
            includeMapping = entriesWithoutOverride.isNotEmpty(),
            operation = operation,
        )
        mapping
            ?.takeIf { entriesWithoutOverride.isNotEmpty() }
            ?.let { storedMapping ->
                val identity = RemoteSeriesIdentity(storedMapping.sourceId, storedMapping.mangaUrl)
                allow(identity, entriesWithoutOverride.map(ReadingListEntry::id))
                entries.forEach { entry ->
                    val override = operation.entryOverrideById[entry.id]
                    if (
                        override?.sourceId == storedMapping.sourceId &&
                        (override.mangaUrl == null || override.mangaUrl == storedMapping.mangaUrl)
                    ) {
                        allow(identity, listOf(entry.id))
                    }
                }
            }
        entries.forEach { entry ->
            val override = operation.entryOverrideById[entry.id] ?: return@forEach
            val mangaUrl = override.mangaUrl ?: return@forEach
            allow(RemoteSeriesIdentity(override.sourceId, mangaUrl), listOf(entry.id))
        }

        val eligibleSearchEntryIdsBySource = mutableMapOf<Long, MutableSet<Long>>()
        if (confirmedMapping == null && entriesWithoutOverride.isNotEmpty()) {
            operation.selectedSources.forEach { source ->
                eligibleSearchEntryIdsBySource
                    .getOrPut(source.source.id, { mutableSetOf() })
                    .addAll(entriesWithoutOverride.map(ReadingListEntry::id))
            }
        }
        entries.forEach { entry ->
            val override = operation.entryOverrideById[entry.id] ?: return@forEach
            if (override.mangaUrl == null && override.sourceId in operation.selectedSourceById) {
                eligibleSearchEntryIdsBySource
                    .getOrPut(override.sourceId, { mutableSetOf() })
                    .add(entry.id)
            }
        }
        val sourcesToSearch = operation.selectedSources.filter { source ->
            eligibleSearchEntryIdsBySource[source.source.id].orEmpty().isNotEmpty()
        }
        val searchedSeeds = sourcesToSearch.map { selectedSource ->
            async {
                searchSourceForSeries(
                    source = selectedSource,
                    entry = representativeEntry,
                    operation = operation,
                )
            }
        }.awaitAll().flatten()
        searchedSeeds.forEach { seed ->
            allow(
                seed.identity,
                eligibleSearchEntryIdsBySource[seed.source.source.id].orEmpty(),
            )
        }
        val confirmedSeed = confirmedMapping
            ?.takeIf { entriesWithoutOverride.isNotEmpty() }
            ?.let { storedMapping ->
                operation.selectedSourceById[storedMapping.sourceId]?.let { source ->
                    RemoteSeriesSeed(
                        source = source,
                        manga = remoteManga(
                            url = storedMapping.mangaUrl,
                            title = storedMapping.seriesTitle,
                        ),
                        priority = SeriesSeedPriority.CONFIRMED_MAPPING,
                    )
                }
            }

        val seeds = (listOfNotNull(confirmedSeed) + directSeeds + searchedSeeds)
            .distinctBy(RemoteSeriesSeed::identity)
            .sortedWith(
                compareBy<RemoteSeriesSeed>(
                    RemoteSeriesSeed::priority,
                    { seed -> seed.source.order },
                    { seed -> -scorer.titleSimilarity(representativeEntry.series, seed.manga.title) },
                    { seed -> seed.manga.url },
                ),
            )

        val fetchedSeries = seeds.map { seed ->
            async {
                fetchSeries(seed, operation)
            }
        }.awaitAll().filterNotNull()

        entries.sortedBy(ReadingListEntry::position).forEach { entry ->
            val override = operation.entryOverrideById[entry.id]
            val protectedSourceUnavailable = when {
                override != null -> override.sourceId !in operation.selectedSourceById
                confirmedMapping != null -> confirmedMapping.sourceId !in operation.selectedSourceById
                else -> false
            }
            if (protectedSourceUnavailable) {
                persistSourceUnavailable(entry, accumulator)
                return@forEach
            }

            val entryDecision = decideEntry(
                entry = entry,
                seriesKey = seriesKey,
                mapping = mapping,
                fetchedSeries = fetchedSeries.filter { content ->
                    entry.id in eligibleEntryIdsBySeries[
                        RemoteSeriesIdentity(
                            sourceId = content.source.source.id,
                            mangaUrl = content.manga.url,
                        ),
                    ].orEmpty()
                },
                operation = operation,
            )
            persistDecision(
                entry = entry,
                entryDecision = entryDecision,
                operation = operation,
                accumulator = accumulator,
            )
        }
    }

    private fun buildDirectSeeds(
        entries: List<ReadingListEntry>,
        mapping: ReadingListSeriesMapping?,
        includeMapping: Boolean,
        operation: SearchOperation,
    ): List<RemoteSeriesSeed> {
        val mappingSeed = mapping
            ?.takeIf { includeMapping }
            ?.let { storedMapping ->
                operation.selectedSourceById[storedMapping.sourceId]?.let { source ->
                    RemoteSeriesSeed(
                        source = source,
                        manga = remoteManga(
                            url = storedMapping.mangaUrl,
                            title = storedMapping.seriesTitle,
                        ),
                        priority = if (storedMapping.userConfirmed) {
                            SeriesSeedPriority.CONFIRMED_MAPPING
                        } else {
                            SeriesSeedPriority.SERIES_MAPPING
                        },
                    )
                }
            }

        val overrideSeeds = entries.mapNotNull { entry ->
            val override = operation.entryOverrideById[entry.id] ?: return@mapNotNull null
            val mangaUrl = override.mangaUrl ?: return@mapNotNull null
            val source = operation.selectedSourceById[override.sourceId] ?: return@mapNotNull null
            RemoteSeriesSeed(
                source = source,
                manga = remoteManga(
                    url = mangaUrl,
                    title = entry.series,
                ),
                priority = SeriesSeedPriority.ENTRY_OVERRIDE,
            )
        }

        return listOfNotNull(mappingSeed) + overrideSeeds
    }

    private suspend fun searchSourceForSeries(
        source: SelectedSource,
        entry: ReadingListEntry,
        operation: SearchOperation,
    ): List<RemoteSeriesSeed> {
        val mangas = executeSourceRequest(
            sourceId = source.source.id,
            operation = operation,
        ) {
            source.source.getSearchManga(
                page = 1,
                query = entry.series,
                filters = source.source.getFilterList(),
            ).mangas
        } ?: return emptyList()

        val expectedTitle = TitleNormalizer.normalize(entry.series)
        val expectedYear = entry.year?.trim()?.toIntOrNull() ?: expectedTitle.year
        val expectedVolume = entry.volume?.trim()?.toIntOrNull() ?: expectedTitle.volume

        return mangas
            .asSequence()
            .filter { manga -> manga.url.isNotBlank() && manga.title.isNotBlank() }
            .distinctBy(SManga::url)
            .take(config.maxSeriesResultsPerSource)
            .mapIndexed { sourcePosition, manga ->
                val actualTitle = TitleNormalizer.normalize(manga.title)
                RankedRemoteSeries(
                    manga = manga,
                    sourcePosition = sourcePosition,
                    titleSimilarity = scorer.titleSimilarity(entry.series, manga.title),
                    yearRank = metadataRank(expectedYear, actualTitle.year),
                    volumeRank = metadataRank(expectedVolume, actualTitle.volume),
                )
            }
            .sortedWith(
                compareByDescending<RankedRemoteSeries>(RankedRemoteSeries::titleSimilarity)
                    .thenBy(RankedRemoteSeries::yearRank)
                    .thenBy(RankedRemoteSeries::volumeRank)
                    .thenBy(RankedRemoteSeries::sourcePosition)
                    .thenBy { result -> result.manga.url },
            )
            .take(config.maxSeriesFetchesPerSource)
            .map { result ->
                RemoteSeriesSeed(
                    source = source,
                    manga = result.manga,
                    priority = SeriesSeedPriority.SEARCH_RESULT,
                )
            }
            .toList()
    }

    private suspend fun fetchSeries(
        seed: RemoteSeriesSeed,
        operation: SearchOperation,
    ): RemoteSeriesContent? {
        when (val cached = operation.fetchedSeries[seed.identity]) {
            is RemoteSeriesFetchResult.Success -> return cached.content
            RemoteSeriesFetchResult.Failure -> return null
            null -> Unit
        }

        val update = executeSourceRequest(
            sourceId = seed.source.source.id,
            operation = operation,
        ) {
            seed.source.source.getMangaUpdate(
                manga = seed.manga,
                chapters = emptyList(),
                fetchDetails = false,
                fetchChapters = true,
            )
        } ?: run {
            operation.fetchedSeries[seed.identity] = RemoteSeriesFetchResult.Failure
            return null
        }

        val manga = update.manga
        if (manga.url.isBlank() || manga.title.isBlank()) {
            operation.failedSourceIds += seed.source.source.id
            operation.fetchedSeries[seed.identity] = RemoteSeriesFetchResult.Failure
            return null
        }

        return RemoteSeriesContent(
            source = seed.source,
            manga = manga,
            chapters = update.chapters
                .filter { chapter -> chapter.url.isNotBlank() }
                .distinctBy(SChapter::url),
        ).also { content ->
            operation.fetchedSeries[seed.identity] = RemoteSeriesFetchResult.Success(content)
        }
    }

    private suspend fun <T> executeSourceRequest(
        sourceId: Long,
        operation: SearchOperation,
        block: suspend () -> T,
    ): T? {
        return try {
            operation.requestSemaphore.withPermit {
                withTimeout(config.requestTimeoutMillis) {
                    block()
                }
            }
        } catch (error: TimeoutCancellationException) {
            recordSourceFailure(
                sourceId = sourceId,
                error = error,
                operation = operation,
            )
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordSourceFailure(
                sourceId = sourceId,
                error = error,
                operation = operation,
            )
            null
        }
    }

    private fun decideEntry(
        entry: ReadingListEntry,
        seriesKey: String,
        mapping: ReadingListSeriesMapping?,
        fetchedSeries: List<RemoteSeriesContent>,
        operation: SearchOperation,
    ): EntryCandidateDecision {
        val query = ReadingListMatchQuery(
            seriesTitle = entry.series,
            issueNumber = entry.number,
            volume = entry.volume?.trim()?.toIntOrNull(),
            year = entry.year?.trim()?.toIntOrNull(),
        )
        val override = operation.entryOverrideById[entry.id]
        val candidates = linkedMapOf<ReadingListCandidateIdentity, ReadingListMatchCandidate>()

        fetchedSeries.asSequence()
            .filter { content ->
                override == null ||
                    (
                        content.source.source.id == override.sourceId &&
                            (override.mangaUrl == null || content.manga.url == override.mangaUrl)
                        )
            }
            .forEach { content ->
                selectChapterCandidates(
                    entry = entry,
                    content = content,
                    requiredChapterUrl = override?.chapterUrl,
                ).forEach { parsedChapter ->
                    val candidate = buildMatchCandidate(
                        entry = entry,
                        seriesKey = seriesKey,
                        mapping = mapping,
                        content = content,
                        parsedChapter = parsedChapter,
                        operation = operation,
                    )
                    candidates.putIfAbsent(candidate.identity, candidate.candidate)
                }
            }

        val rejectedIdentities = operation.rejectedCandidateIdentitiesByEntryId[entry.id].orEmpty()
        val acceptableCandidates = candidates.filterKeys { identity ->
            identity !in rejectedIdentities
        }
        val rejectedCandidates = candidates.filterKeys { identity ->
            identity in rejectedIdentities
        }
        val decision = scorer.decide(
            query = query,
            candidates = acceptableCandidates.values.toList(),
        )
        val rejectedRankedCandidates = if (rejectedCandidates.isEmpty()) {
            emptyList()
        } else {
            scorer.decide(
                query = query,
                candidates = rejectedCandidates.values.toList(),
            ).rankedCandidates
        }

        return EntryCandidateDecision(
            decision = decision,
            rankedCandidates = decision.rankedCandidates + rejectedRankedCandidates,
        )
    }

    private fun selectChapterCandidates(
        entry: ReadingListEntry,
        content: RemoteSeriesContent,
        requiredChapterUrl: String?,
    ): List<ParsedChapter> {
        val expectedIssue = IssueNumberNormalizer.normalize(entry.number)
        val parsedChapters = content.chapters
            .filter { chapter ->
                requiredChapterUrl == null || chapter.url == requiredChapterUrl
            }
            .mapNotNull { chapter ->
                val issueText = ReadingListChapterIssueExtractor.extract(
                    seriesTitle = content.manga.title,
                    chapterName = chapter.name,
                    chapterNumber = chapter.chapter_number,
                    expectedIssue = entry.number,
                ) ?: requiredChapterUrl?.let { entry.number } ?: return@mapNotNull null
                val normalizedIssue = IssueNumberNormalizer.normalize(issueText)
                if (!normalizedIssue.isUsable) return@mapNotNull null

                ParsedChapter(
                    chapter = chapter,
                    issueText = issueText,
                    normalizedIssue = normalizedIssue,
                    issueEquivalent = expectedIssue.isEquivalentTo(normalizedIssue),
                    numericDistance = issueDistance(expectedIssue, normalizedIssue),
                )
            }
            .sortedWith(
                compareByDescending<ParsedChapter>(ParsedChapter::issueEquivalent)
                    .thenBy { chapter -> chapter.numericDistance ?: DISTANT_ISSUE }
                    .thenBy { chapter -> chapter.chapter.url },
            )
        val equivalentChapters = parsedChapters.filter(ParsedChapter::issueEquivalent)

        return (equivalentChapters.ifEmpty { parsedChapters })
            .take(config.maxIssueCandidatesPerSeries)
    }

    private fun buildMatchCandidate(
        entry: ReadingListEntry,
        seriesKey: String,
        mapping: ReadingListSeriesMapping?,
        content: RemoteSeriesContent,
        parsedChapter: ParsedChapter,
        operation: SearchOperation,
    ): CandidateWithIdentity {
        val source = content.source
        val mangaUrl = content.manga.url
        val chapterUrl = parsedChapter.chapter.url
        val identity = ReadingListCandidateIdentity(
            sourceId = source.source.id,
            candidateId = stableCandidateId(mangaUrl, chapterUrl),
        )
        val titleMetadata = TitleNormalizer.normalize(content.manga.title)

        return CandidateWithIdentity(
            identity = identity,
            candidate = ReadingListMatchCandidate(
                id = identity.candidateId,
                sourceId = source.source.id,
                sourceOrder = source.order,
                mangaUrl = mangaUrl,
                chapterUrl = chapterUrl,
                seriesTitle = content.manga.title,
                issueNumber = parsedChapter.issueText,
                volume = titleMetadata.volume,
                year = titleMetadata.year,
                sourcePreference = sourcePreference(
                    entry = entry,
                    sourceId = source.source.id,
                    mangaUrl = mangaUrl,
                    mapping = mapping,
                    operation = operation,
                ),
                externalIdentifierEvidence = EvidenceAgreement.UNKNOWN,
                confirmedHistory = confirmedHistory(
                    sourceId = source.source.id,
                    mangaUrl = mangaUrl,
                    seriesKey = seriesKey,
                    mapping = mapping,
                    operation = operation,
                ),
            ),
        )
    }

    private fun sourcePreference(
        entry: ReadingListEntry,
        sourceId: Long,
        mangaUrl: String,
        mapping: ReadingListSeriesMapping?,
        operation: SearchOperation,
    ): SourcePreferenceLevel {
        val override = operation.entryOverrideById[entry.id]
        if (
            override?.sourceId == sourceId &&
            (override.mangaUrl == null || override.mangaUrl == mangaUrl)
        ) {
            return SourcePreferenceLevel.ENTRY
        }
        if (mapping?.sourceId == sourceId && mapping.mangaUrl == mangaUrl) {
            return SourcePreferenceLevel.SERIES
        }
        return SourcePreferenceLevel.READING_LIST
    }

    private fun confirmedHistory(
        sourceId: Long,
        mangaUrl: String,
        seriesKey: String,
        mapping: ReadingListSeriesMapping?,
        operation: SearchOperation,
    ): ConfirmedHistoryEvidence {
        if (
            mapping?.userConfirmed == true &&
            mapping.sourceId == sourceId &&
            mapping.mangaUrl == mangaUrl
        ) {
            return ConfirmedHistoryEvidence.SERIES
        }
        if (
            operation.readingList.entries.any { entry ->
                entry.userConfirmed &&
                    entry.matchedSourceId == sourceId &&
                    entry.matchedMangaUrl == mangaUrl &&
                    ReadingListSeriesKey.from(
                        seriesTitle = entry.series,
                        volume = entry.volume,
                        year = entry.year,
                    ) == seriesKey
            }
        ) {
            return ConfirmedHistoryEvidence.SERIES
        }
        if (
            operation.readingList.entries.any { entry ->
                entry.userConfirmed && entry.matchedSourceId == sourceId
            }
        ) {
            return ConfirmedHistoryEvidence.SOURCE
        }
        return ConfirmedHistoryEvidence.NONE
    }

    private suspend fun persistDecision(
        entry: ReadingListEntry,
        entryDecision: EntryCandidateDecision,
        operation: SearchOperation,
        accumulator: SearchAccumulator,
    ) {
        val decision = entryDecision.decision
        val rankedCandidates = entryDecision.rankedCandidates
            .take(config.maxCandidatesPerEntry)
        val snapshots = rankedCandidates.map { scoredCandidate ->
            scoredCandidate.toSnapshot(
                decision = decision,
                matcherVersion = config.matcherVersion,
                operation = operation,
            )
        }
        val acceptedIdentity = decision.acceptedCandidate
            ?.candidate
            ?.let { candidate ->
                ReadingListCandidateIdentity(
                    sourceId = candidate.sourceId,
                    candidateId = candidate.id,
                )
            }
        val acceptedCandidate = acceptedIdentity?.let { identity ->
            snapshots.firstOrNull { candidate -> candidate.identity == identity }
        }
        val update = ReadingListAutomaticResolutionUpdate(
            state = decision.state,
            leadingConfidence = decision.leadingCandidate?.score,
            matcherVersion = config.matcherVersion,
            acceptedCandidate = acceptedCandidate,
        )

        when (
            resolutionRepository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = entry.id,
                candidates = snapshots,
                update = update,
            )
        ) {
            ReadingListProtectedWriteResult.APPLIED -> {
                accumulator.searchedEntries += 1
                accumulator.candidateCount += snapshots.size
                when (decision.state) {
                    ReadingListEntryResolutionState.AUTO_MATCHED -> accumulator.autoMatchedEntries += 1
                    ReadingListEntryResolutionState.AMBIGUOUS -> accumulator.reviewEntries += 1
                    ReadingListEntryResolutionState.UNRESOLVED -> accumulator.unresolvedEntries += 1
                    else -> error("Unexpected automatic candidate-search state: ${decision.state}")
                }
            }
            ReadingListProtectedWriteResult.USER_CONFIRMED,
            ReadingListProtectedWriteResult.SKIPPED,
            -> accumulator.protectedEntries += 1
            ReadingListProtectedWriteResult.ENTRY_NOT_FOUND -> accumulator.missingEntries += 1
        }
    }

    private suspend fun persistSourceUnavailable(
        entry: ReadingListEntry,
        accumulator: SearchAccumulator,
    ) {
        val update = ReadingListAutomaticResolutionUpdate(
            state = ReadingListEntryResolutionState.SOURCE_UNAVAILABLE,
            leadingConfidence = null,
            matcherVersion = config.matcherVersion,
            acceptedCandidate = null,
        )
        when (
            resolutionRepository.replaceMatchCandidatesAndApplyAutomaticResolution(
                entryId = entry.id,
                candidates = emptyList(),
                update = update,
            )
        ) {
            ReadingListProtectedWriteResult.APPLIED -> {
                accumulator.searchedEntries += 1
                accumulator.sourceUnavailableEntries += 1
            }
            ReadingListProtectedWriteResult.USER_CONFIRMED,
            ReadingListProtectedWriteResult.SKIPPED,
            -> accumulator.protectedEntries += 1
            ReadingListProtectedWriteResult.ENTRY_NOT_FOUND -> accumulator.missingEntries += 1
        }
    }

    private fun ScoredReadingListMatchCandidate.toSnapshot(
        decision: ReadingListMatchDecision,
        matcherVersion: Long,
        operation: SearchOperation,
    ): ReadingListMatchCandidateSnapshot {
        val selectedSource = operation.selectedSourceById[candidate.sourceId]
            ?: error("Candidate source is no longer part of this search")
        return ReadingListMatchCandidateSnapshot(
            identity = ReadingListCandidateIdentity(
                sourceId = candidate.sourceId,
                candidateId = candidate.id,
            ),
            sourceName = selectedSource.source.name.ifBlank { candidate.sourceId.toString() },
            sourceLanguage = selectedSource.source.lang.ifBlank { UNDEFINED_LANGUAGE },
            mangaUrl = requireNotNull(candidate.mangaUrl) {
                "Candidate manga URL was not retained"
            },
            chapterUrl = requireNotNull(candidate.chapterUrl) {
                "Candidate chapter URL was not retained"
            },
            seriesTitle = candidate.seriesTitle,
            issueNumber = candidate.issueNumber,
            volume = candidate.volume,
            year = candidate.year,
            breakdown = breakdown,
            decisionReason = decision.reason,
            leadOverRunnerUp = decision.leadOverRunnerUp,
            matcherVersion = matcherVersion,
        )
    }

    private fun stableCandidateId(
        mangaUrl: String,
        chapterUrl: String,
    ): String {
        val input = "$mangaUrl\u0000$chapterUrl"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private fun recordSourceFailure(
        sourceId: Long,
        error: Exception,
        operation: SearchOperation,
    ) {
        operation.failedSourceIds += sourceId
        val recommendsUpdate = generateSequence(error as Throwable?) { throwable ->
            throwable.cause
        }.any { throwable ->
            throwable is NullPointerException ||
                throwable.message.orEmpty()
                    .contains("NullPointerException", ignoreCase = true)
        }
        if (recommendsUpdate) {
            operation.updateRecommendedSourceIds += sourceId
        }
    }

    private fun metadataRank(
        expected: Int?,
        actual: Int?,
    ): Int {
        return when {
            expected == null || actual == null -> 1
            expected == actual -> 0
            else -> 2
        }
    }

    private fun issueDistance(
        expected: NormalizedIssueNumber,
        actual: NormalizedIssueNumber,
    ): BigDecimal? {
        if (expected.kind != actual.kind) {
            return null
        }

        val expectedValue = expected.numericValue ?: return null
        val actualValue = actual.numericValue ?: return null
        return expectedValue.subtract(actualValue).abs()
    }

    private fun remoteManga(
        url: String,
        title: String,
    ): SManga {
        return SManga.create().apply {
            this.url = url
            this.title = title
        }
    }

    private data class SearchOperation(
        val readingList: ReadingList,
        val resolution: ReadingListResolutionData,
        val selectedSources: List<SelectedSource>,
        val requestSemaphore: Semaphore,
        val failedSourceIds: MutableSet<Long> = ConcurrentHashMap.newKeySet(),
        val updateRecommendedSourceIds: MutableSet<Long> = ConcurrentHashMap.newKeySet(),
        val fetchedSeries: MutableMap<RemoteSeriesIdentity, RemoteSeriesFetchResult> = ConcurrentHashMap(),
    ) {
        val selectedSourceById = selectedSources.associateBy { selectedSource ->
            selectedSource.source.id
        }
        val entryOverrideById = resolution.entryOverrides.associateBy(ReadingListEntryOverride::entryId)
        val rejectedCandidateIdentitiesByEntryId = resolution.rejections
            .groupBy(
                keySelector = { rejection -> rejection.entryId },
                valueTransform = { rejection -> rejection.identity },
            )
            .mapValues { (_, identities) -> identities.toSet() }
    }

    private data class SelectedSource(
        val source: HttpSource,
        val order: Int,
    )

    private data class RankedRemoteSeries(
        val manga: SManga,
        val sourcePosition: Int,
        val titleSimilarity: Double,
        val yearRank: Int,
        val volumeRank: Int,
    )

    private data class RemoteSeriesSeed(
        val source: SelectedSource,
        val manga: SManga,
        val priority: SeriesSeedPriority,
    ) {
        val identity: RemoteSeriesIdentity
            get() = RemoteSeriesIdentity(
                sourceId = source.source.id,
                mangaUrl = manga.url,
            )
    }

    private data class RemoteSeriesIdentity(
        val sourceId: Long,
        val mangaUrl: String,
    )

    private data class RemoteSeriesContent(
        val source: SelectedSource,
        val manga: SManga,
        val chapters: List<SChapter>,
    )

    private sealed interface RemoteSeriesFetchResult {
        data class Success(
            val content: RemoteSeriesContent,
        ) : RemoteSeriesFetchResult

        data object Failure : RemoteSeriesFetchResult
    }

    private data class ParsedChapter(
        val chapter: SChapter,
        val issueText: String,
        val normalizedIssue: NormalizedIssueNumber,
        val issueEquivalent: Boolean,
        val numericDistance: BigDecimal?,
    )

    private data class EntryCandidateDecision(
        val decision: ReadingListMatchDecision,
        val rankedCandidates: List<ScoredReadingListMatchCandidate>,
    )

    private data class CandidateWithIdentity(
        val identity: ReadingListCandidateIdentity,
        val candidate: ReadingListMatchCandidate,
    )

    private enum class SeriesSeedPriority {
        CONFIRMED_MAPPING,
        ENTRY_OVERRIDE,
        SERIES_MAPPING,
        SEARCH_RESULT,
    }

    private class SearchAccumulator(
        private val selectedSourceCount: Int,
        private val unavailableSelectedSourceCount: Int,
    ) {
        var searchedEntries: Int = 0
        var autoMatchedEntries: Int = 0
        var reviewEntries: Int = 0
        var unresolvedEntries: Int = 0
        var sourceUnavailableEntries: Int = 0
        var protectedEntries: Int = 0
        var missingEntries: Int = 0
        var candidateCount: Int = 0
        var failedSourceCount: Int = 0
        var updateRecommendedSourceCount: Int = 0

        fun toSummary(): ReadingListCandidateSearchSummary {
            return ReadingListCandidateSearchSummary(
                searchedEntries = searchedEntries,
                autoMatchedEntries = autoMatchedEntries,
                reviewEntries = reviewEntries,
                unresolvedEntries = unresolvedEntries,
                sourceUnavailableEntries = sourceUnavailableEntries,
                protectedEntries = protectedEntries,
                missingEntries = missingEntries,
                candidateCount = candidateCount,
                unavailableSelectedSourceCount = unavailableSelectedSourceCount,
                allSelectedSourcesUnavailable =
                selectedSourceCount > 0 &&
                    unavailableSelectedSourceCount == selectedSourceCount,
                failedSourceCount = failedSourceCount,
                updateRecommendedSourceCount = updateRecommendedSourceCount,
            )
        }
    }

    private companion object {
        const val UNDEFINED_LANGUAGE = "und"
        val DISTANT_ISSUE = BigDecimal("1E100")
    }
}

data class ReadingListCandidateSearchConfig(
    val matcherVersion: Long = 1,
    val maxConcurrentRequests: Int = 3,
    val requestTimeoutMillis: Long = 30_000,
    val maxSeriesResultsPerSource: Int = 10,
    val maxSeriesFetchesPerSource: Int = 3,
    val maxIssueCandidatesPerSeries: Int = 3,
    val maxCandidatesPerEntry: Int = 24,
) {
    init {
        require(matcherVersion > 0)
        require(maxConcurrentRequests > 0)
        require(requestTimeoutMillis > 0)
        require(maxSeriesResultsPerSource > 0)
        require(maxSeriesFetchesPerSource in 1..maxSeriesResultsPerSource)
        require(maxIssueCandidatesPerSeries > 0)
        require(maxCandidatesPerEntry > 0)
    }
}

sealed interface ReadingListCandidateSearchResult {
    data object ReadingListNotFound : ReadingListCandidateSearchResult

    data class Completed(
        val summary: ReadingListCandidateSearchSummary,
    ) : ReadingListCandidateSearchResult
}

data class ReadingListCandidateSearchSummary(
    val searchedEntries: Int,
    val autoMatchedEntries: Int,
    val reviewEntries: Int,
    val unresolvedEntries: Int,
    val sourceUnavailableEntries: Int,
    val protectedEntries: Int,
    val missingEntries: Int,
    val candidateCount: Int,
    val unavailableSelectedSourceCount: Int,
    val allSelectedSourcesUnavailable: Boolean,
    val failedSourceCount: Int,
    val updateRecommendedSourceCount: Int,
)
