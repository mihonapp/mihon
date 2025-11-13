package eu.kanade.presentation.manga.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DotSeparatorText(
    modifier: Modifier = Modifier,
) {
    Text(
        text = " • ",
        modifier = modifier,
    )
}

@Composable
fun DotSeparatorNoSpaceText(
    modifier: Modifier = Modifier,
) {
    Text(
        text = "•",
        modifier = modifier,
    )
}
