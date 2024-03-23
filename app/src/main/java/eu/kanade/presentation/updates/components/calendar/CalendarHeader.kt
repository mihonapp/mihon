package eu.kanade.presentation.updates.components.calendar

import android.content.Context
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val HEADER_PADDING = 8.dp

@Composable
fun CalenderHeader(
    month: Month,
    year: Int,
    modifier: Modifier = Modifier,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    arrowShown: Boolean = true,
) {
    var isNext by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(all = HEADER_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val titleText = remember(month, year) { getTitleText(context, month, year) }

        AnimatedContent(
            targetState = titleText,
            transitionSpec = {
                addAnimation(isNext = isNext).using(
                    SizeTransform(clip = false),
                )
            },
            label = "Change Month",
        ) { targetText ->
            Text(
                text = targetText,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (arrowShown) {
            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = {
                        isNext = false
                        onPreviousClick()
                    },
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CircleShape),
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, stringResource(MR.strings.upcoming_calendar_prev))
                }

                IconButton(
                    onClick = {
                        isNext = true
                        onNextClick()
                    },
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CircleShape),
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, stringResource(MR.strings.upcoming_calendar_next))
                }
            }
        }
    }
}

/**
 * Adds the animation to the content based on the given duration and direction.
 *
 * @param duration The duration of the animation in milliseconds.
 * @param isNext Determines the direction of the animation.
 * @return The content transformation with the specified animation.
 */
private fun addAnimation(duration: Int = 200, isNext: Boolean): ContentTransform {
    val enterTransition = slideInVertically(
        animationSpec = tween(durationMillis = duration),
    ) { height -> if (isNext) height else -height } + fadeIn(
        animationSpec = tween(durationMillis = duration),
    )
    val exitTransition = slideOutVertically(
        animationSpec = tween(durationMillis = duration),
    ) { height -> if (isNext) -height else height } + fadeOut(
        animationSpec = tween(durationMillis = duration),
    )
    return enterTransition togetherWith exitTransition
}

/**
 * Returns the formatted title text for the Calendar header.
 *
 * @param month The current month.
 * @param year The current year.
 * @return The formatted title text.
 */
private fun getTitleText(context: Context, month: Month, year: Int): String {
    val monthDisplayName = month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val shortYear = year.toString().takeLast(2)
    return context.resources.getString(MR.strings.calendar_title_format.resourceId, monthDisplayName, shortYear)
}

@Preview
@Composable
private fun CalenderHeaderPreview() {
    CalenderHeader(
        month = Month.APRIL,
        year = 2024,
    )
}