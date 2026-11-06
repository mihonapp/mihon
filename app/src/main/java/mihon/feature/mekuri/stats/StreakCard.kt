package mihon.feature.mekuri.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.repository.HistoryRepository
import kotlin.time.Clock

/**
 * Mekuri: reading streaks at the top of Statistics: days in a row with at least one chapter read, the best run,
 * and chapters read in the last 7 days.
 */
@Composable
fun StreakCard(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<StreakViewModel>()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val data = streak ?: return

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Reading streak",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Figure(value = data.current, label = if (data.current == 1) "day now" else "days now")
                Figure(value = data.best, label = "best")
                val weekLabel = if (data.thisWeek == 1) "chapter this week" else "chapters this week"
                Figure(value = data.thisWeek, label = weekLabel)
            }
            if (data.current == 0) {
                Text(
                    text = "Read a chapter today to start a new streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Figure(value: Int, label: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class StreakViewModel(
    getHistory: GetHistory,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    data class Streak(val current: Int, val best: Int, val thisWeek: Int)

    val streak: StateFlow<Streak?> = getHistory.subscribe("")
        .map { latest ->
            val all = historyRepository.getHistoryByMangaIds(latest.map { it.mangaId }.distinct())
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val reads = all.values.flatten().mapNotNull { it.readAt }
            val days = reads.map { it.toLocalDateTime(zone).date }.toSortedSet()
            val today = now.toLocalDateTime(zone).date
            val weekAgo = now.minus(kotlin.time.Duration.parse("7d"))
            Streak(
                current = currentRun(days, today),
                best = bestRun(days),
                thisWeek = reads.count { it > weekAgo },
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Days in a row ending today (or yesterday, so the streak isn't lost before you read today). */
    private fun currentRun(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minus(DatePeriod(days = 1))
        var count = 0
        while (day in days) {
            count++
            day = day.minus(DatePeriod(days = 1))
        }
        return count
    }

    private fun bestRun(days: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.minus(DatePeriod(days = -1)) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }
}
