package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

val StartItemShape = RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
val MiddleItemShape = RoundedCornerShape(0)
val EndItemShape = RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)

@Composable
fun SegmentedButtons(
    modifier: Modifier = Modifier,
    entries: List<String>,
    selectedIndex: Int,
    onClick: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
    ) {
        entries.mapIndexed { index, label ->
            val shape = remember(entries, index) {
                when (index) {
                    0 -> StartItemShape
                    entries.lastIndex -> EndItemShape
                    else -> MiddleItemShape
                }
            }

            if (index == selectedIndex) {
                Button(
                    modifier = Modifier.weight(1f),
                    shape = shape,
                    onClick = { onClick(index) },
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = shape,
                    onClick = { onClick(index) },
                ) {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SegmentedButtonsPreview() {
    Column {
        SegmentedButtons(
            entries = listOf(
                "Day",
                "Week",
                "Month",
                "Year",
            ),
            selectedIndex = 1,
            onClick = {},
        )

        SegmentedButtons(
            entries = listOf(
                "Foo",
                "Bar",
            ),
            selectedIndex = 1,
            onClick = {},
        )
    }
}
