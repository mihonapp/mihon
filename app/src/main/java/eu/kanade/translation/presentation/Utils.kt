package eu.kanade.translation.presentation
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Float.pxToDp(): Dp {
    return (this / LocalDensity.current.density).dp
//    val density = LocalDensity.current.density
//    return (this / (density / DisplayMetrics.DENSITY_DEFAULT)).dp
}
