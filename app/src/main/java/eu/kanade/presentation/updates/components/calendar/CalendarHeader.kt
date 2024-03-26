package eu.kanade.presentation.updates.components.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalenderHeader(
    yearMonth: YearMonth,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = yearMonth,
            transitionSpec = { getAnimation() },
            label = "Change Month",
        ) { monthYear ->
            Text(
                text = getTitleText(monthYear),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Row {
            IconButton(onClick = onPreviousClick) {
                Icon(Icons.Default.KeyboardArrowLeft, stringResource(MR.strings.upcoming_calendar_prev))
            }
            IconButton(onClick = onNextClick) {
                Icon(Icons.Default.KeyboardArrowRight, stringResource(MR.strings.upcoming_calendar_next))
            }
        }
    }
}

private const val MonthYearChangeAnimationDuration = 200

private fun AnimatedContentTransitionScope<YearMonth>.getAnimation(): ContentTransform {
    val movingForward = targetState > initialState

    val enterTransition = slideInVertically(
        animationSpec = tween(durationMillis = MonthYearChangeAnimationDuration),
    ) { height -> if (movingForward) height else -height } + fadeIn(
        animationSpec = tween(durationMillis = MonthYearChangeAnimationDuration),
    )
    val exitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = MonthYearChangeAnimationDuration),
    ) { height -> if (movingForward) -height else height } + fadeOut(
        animationSpec = tween(durationMillis = MonthYearChangeAnimationDuration),
    )
    return (enterTransition togetherWith exitTransition)
        .using(SizeTransform(clip = false))
}

/**
 * Returns the formatted title text for the Calendar header.
 *
 * @param monthYear The current month and year pair.
 * @return The formatted title text.
 */
@Composable
@ReadOnlyComposable
private fun getTitleText(monthYear: YearMonth): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    return formatter.format(monthYear)
}

@Preview
@Composable
private fun CalenderHeaderPreview() {
    CalenderHeader(
        yearMonth = YearMonth.now(),
        onNextClick = {},
        onPreviousClick = {},
    )
}
