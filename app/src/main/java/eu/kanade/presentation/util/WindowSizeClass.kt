package eu.kanade.presentation.util

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
@ReadOnlyComposable
fun calculateWindowWidthSizeClass(): WindowWidthSizeClass {
    val configuration = LocalConfiguration.current
    return fromWidth(configuration.smallestScreenWidthDp.dp)
}

private fun fromWidth(width: Dp): WindowWidthSizeClass {
    require(width >= 0.dp) { "Width must not be negative" }
    return when {
        width < 720.dp -> WindowWidthSizeClass.Compact // Was 600
        width < 840.dp -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
}
