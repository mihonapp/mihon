package eu.kanade.tachiyomi.ui.updates.calendar

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalenderHeader(
    month: Month,
    year: Int,
    modifier: Modifier = Modifier,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    arrowShown: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(all = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val titleText = remember(month, year) { getTitleText(month, year) }

        Text(text = titleText,
            style = MaterialTheme.typography.titleLarge)

        if (arrowShown) {
            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onPreviousClick,
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CircleShape),

                    ) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Previous Month")
                }

                IconButton(onClick = onNextClick,
                    modifier = Modifier
                        .wrapContentSize()
                        .clip(CircleShape),

                    ) {
                    Icon(Icons.Default.KeyboardArrowRight, "Next Month")
                }
            }
        }


    }


}

/**
 * Returns the formatted title text for the Kalendar header.
 *
 * @param month The current month.
 * @param year The current year.
 * @return The formatted title text.
 */
private fun getTitleText(month: Month, year: Int): String {
    val monthDisplayName = month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val shortYear = year.toString().takeLast(2)
    return "$monthDisplayName '$shortYear"
}

@Preview
@Composable
fun CalenderHeaderPreview() {
    CalenderHeader(
        month = Month.APRIL,
        year = 2024
    )
}
