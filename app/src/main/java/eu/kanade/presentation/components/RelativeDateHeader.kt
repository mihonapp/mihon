package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.util.lang.toRelativeString
import tachiyomi.presentation.core.components.ListGroupHeader
import java.text.DateFormat
import java.util.Date

@Composable
fun RelativeDateHeader(
    modifier: Modifier = Modifier,
    date: Date,
    dateFormat: DateFormat,
) {
    val context = LocalContext.current
    ListGroupHeader(
        modifier = modifier,
        text = remember {
            date.toRelativeString(context, dateFormat)
        },
    )
}
