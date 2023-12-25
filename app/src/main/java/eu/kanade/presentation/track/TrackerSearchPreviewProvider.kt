package eu.kanade.presentation.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

internal class TrackerSearchPreviewProvider : PreviewParameterProvider<@Composable () -> Unit> {
    private val fullPageWithSecondSelected = @Composable {
        val items = someTrackSearches().take(30).toList()
        TrackerSearch(
            query = TextFieldValue(text = "search text"),
            onQueryChange = {},
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
        )
    }
    private val fullPageWithoutSelected = @Composable {
        TrackerSearch(
            query = TextFieldValue(text = ""),
            onQueryChange = {},
            onDispatchQuery = {},
            queryResult = Result.success(someTrackSearches().take(30).toList()),
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
        )
    }
    private val loading = @Composable {
        TrackerSearch(
            query = TextFieldValue(),
            onQueryChange = {},
            onDispatchQuery = {},
            queryResult = null,
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
        )
    }
    override val values: Sequence<@Composable () -> Unit> = sequenceOf(
        fullPageWithSecondSelected,
        fullPageWithoutSelected,
        loading,
    )

    private fun someTrackSearches(): Sequence<TrackSearch> = sequence {
        while (true) {
            yield(randTrackSearch())
        }
    }

    private fun randTrackSearch() = TrackSearch().let {
        it.id = Random.nextLong()
        it.manga_id = Random.nextLong()
        it.tracker_id = Random.nextInt()
        it.remote_id = Random.nextLong()
        it.library_id = Random.nextLong()
        it.title = lorem((1..10).random()).joinToString()
        it.last_chapter_read = (0..100).random().toFloat()
        it.total_chapters = (100..1000).random()
        it.score = (0..10).random().toFloat()
        it.status = Random.nextInt()
        it.started_reading_date = 0L
        it.finished_reading_date = 0L
        it.tracking_url = "https://example.com/tracker-example"
        it.cover_url = "https://example.com/cover.png"
        it.start_date = Instant.now().minus((1L..365).random(), ChronoUnit.DAYS).toString()
        it.summary = lorem((0..40).random()).joinToString()
        it
    }

    private fun lorem(words: Int): Sequence<String> =
        LoremIpsum(words).values
}
