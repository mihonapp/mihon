package eu.kanade.presentation.track

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.random.Random

internal class TrackerSearchPreviewProvider : PreviewParameterProvider<@Composable () -> Unit> {
    private val fullPageWithSecondSelected = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithoutSelected = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = Result.success(someTrackSearches().take(30).toList()),
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val loading = @Composable {
        TrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = null,
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithPrivateTracking = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = true,
        )
    }
    override val values: Sequence<@Composable () -> Unit> = sequenceOf(
        fullPageWithSecondSelected,
        fullPageWithoutSelected,
        loading,
        fullPageWithPrivateTracking,
    )

    private fun someTrackSearches(): Sequence<TrackSearch> = sequence {
        while (true) {
            yield(randTrackSearch())
        }
    }

    private val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun randTrackSearch() = TrackSearch().let {
        it.id = Random.nextLong()
        it.manga_id = Random.nextLong()
        it.tracker_id = Random.nextLong()
        it.remote_id = Random.nextLong()
        it.library_id = Random.nextLong()
        it.title = lorem((1..10).random()).joinToString()
        it.last_chapter_read = (0..100).random().toDouble()
        it.total_chapters = (100L..1000L).random()
        it.score = (0..10).random().toDouble()
        it.status = Random.nextLong()
        it.started_reading_date = 0L
        it.finished_reading_date = 0L
        it.tracking_url = "https://example.com/tracker-example"
        it.cover_url = "https://example.com/cover.png"
        it.start_date = formatter.format(Date.from(Instant.now().minus((1L..365).random(), ChronoUnit.DAYS)))
        it.summary = lorem((0..40).random()).joinToString()
        it.publishing_status = if (Random.nextBoolean()) "Finished" else ""
        it.publishing_type = if (Random.nextBoolean()) "Oneshot" else ""
        it
    }

    private fun lorem(words: Int): Sequence<String> =
        LoremIpsum(words).values
}
