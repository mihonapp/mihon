package eu.kanade.presentation.manga.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun DotSeparatorText() {
    Text(text = " • ")
}

@Composable
fun DotSeparatorNoSpaceText() {
    Text(text = "•")
}
