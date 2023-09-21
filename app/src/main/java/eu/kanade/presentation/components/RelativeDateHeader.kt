package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tachiyomi.presentation.core.components.ListGroupHeader
import java.text.DateFormat
import java.util.Date

@Composable
fun RelativeDateHeader(
    modifier: Modifier = Modifier,
    date: Date,
    dateFormat: DateFormat,
) {
    ListGroupHeader(
        modifier = modifier,
        text = remember {
            dateFormat.format(date)
        },
    )
}
