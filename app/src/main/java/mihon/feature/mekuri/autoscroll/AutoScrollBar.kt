package mihon.feature.mekuri.autoscroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow

/**
 * Mekuri: the auto-scroll control shown above the reader's bottom bar: play/pause plus a speed slider.
 */
@Composable
fun AutoScrollBar(
    playing: Boolean,
    onTogglePlaying: () -> Unit,
    speed: Int,
    onSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledIconButton(
            onClick = onTogglePlaying,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (playing) {
                    MaterialSymbols.RoundedFilled.Pause
                } else {
                    MaterialSymbols.RoundedFilled.PlayArrow
                },
                contentDescription = if (playing) "Pause auto-scroll" else "Start auto-scroll",
            )
        }
        Slider(
            value = speed.toFloat(),
            onValueChange = { onSpeedChange(it.toInt()) },
            valueRange = AutoScroller.SPEED_RANGE.first.toFloat()..AutoScroller.SPEED_RANGE.last.toFloat(),
            steps = AutoScroller.SPEED_RANGE.last - AutoScroller.SPEED_RANGE.first - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = AutoScroller.label(speed),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}
