package eu.kanade.presentation.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover
import java.util.Date
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class HistoryScreenModelStateProvider : PreviewParameterProvider<HistoryScreenModel.State> {

    private val multiPage = HistoryScreenModel.State(
        searchQuery = null,
        list =
        listOf(HistoryUiModelExamples.headerToday)
            .asSequence()
            .plus(HistoryUiModelExamples.items().take(3))
            .plus(HistoryUiModelExamples.header { it.minus(1.days) })
            .plus(HistoryUiModelExamples.items().take(1))
            .plus(HistoryUiModelExamples.header { it.minus(2.days) })
            .plus(HistoryUiModelExamples.items().take(7))
            .toList(),
        dialog = null,
    )

    private val shortRecent = HistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val shortFuture = HistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items().first(),
        ),
        dialog = null,
    )

    private val empty = HistoryScreenModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = HistoryScreenModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = HistoryScreenModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<HistoryScreenModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    private object HistoryUiModelExamples {
        val headerToday = header()
        val headerTomorrow =
            HistoryUiModel.Header(Clock.System.now().plus(1.days).toLocalDateTime(TimeZone.currentSystemDefault()).date)

        fun header(instantBuilder: (Instant) -> Instant = { it }) =
            HistoryUiModel.Header(
                instantBuilder(Clock.System.now()).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            )

        fun items() = sequence {
            var count = 1
            while (true) {
                yield(randItem { it.copy(title = "Example Title $count") })
                count += 1
            }
        }

        fun randItem(historyBuilder: (HistoryWithRelations) -> HistoryWithRelations = { it }) =
            HistoryUiModel.Item(
                historyBuilder(
                    HistoryWithRelations(
                        id = Random.nextLong(),
                        chapterId = Random.nextLong(),
                        mangaId = Random.nextLong(),
                        title = "Test Title",
                        chapterNumber = Random.nextDouble(),
                        readAt = Date.from(Clock.System.now().toJavaInstant()),
                        readDuration = Random.nextLong(),
                        coverData = MangaCover(
                            mangaId = Random.nextLong(),
                            sourceId = Random.nextLong(),
                            isMangaFavorite = Random.nextBoolean(),
                            url = "https://example.com/cover.png",
                            lastModified = Random.nextLong(),
                        ),
                    ),
                ),
            )
    }
}
