package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

@Composable
internal fun LibraryPageSlider(
    page: Int,
    pageCount: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    showNumbers: Boolean = true,
) {
    val state = key(pageCount) {
        rememberSliderState(
            value = (page + 1).toFloat(),
            steps = (pageCount - 2).coerceAtLeast(0),
            trackRange = 1f..pageCount.coerceAtLeast(2).toFloat(),
        )
    }
    state.value = (page + 1).toFloat()
    val description = stringResource(MR.strings.library_page_number, page + 1, pageCount)
    val onChange: (Float) -> Unit = { onValueChange(it.roundToInt() - 1) }
    val current: @Composable () -> Unit = {
        if (showNumbers) Text((page + 1).toString(), style = MaterialTheme.typography.bodyLarge)
    }
    val total: @Composable () -> Unit = {
        if (showNumbers) Text(pageCount.toString(), style = MaterialTheme.typography.bodyLarge)
    }
    if (vertical) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            current()
            VerticalSlider(
                state = state,
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                    .semantics { contentDescription = description },
                onValueChange = onChange,
                onValueChangeFinished = onValueChangeFinished,
            )
            total()
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            current()
            Slider(
                state = state,
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    .semantics { contentDescription = description },
                onValueChange = onChange,
                onValueChangeFinished = onValueChangeFinished,
            )
            total()
        }
    }
}
