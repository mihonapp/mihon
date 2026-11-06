package mihon.feature.mekuri.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Mekuri: a hairline across the top of the reader showing how far through the chapter you are, so you don't need
 * to open the menu to check.
 */
@Composable
fun ReadingProgressLine(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    if (totalPages <= 0) return
    val progress by animateFloatAsState((currentPage.toFloat() / totalPages).coerceIn(0f, 1f), label = "progress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)),
        )
    }
}
