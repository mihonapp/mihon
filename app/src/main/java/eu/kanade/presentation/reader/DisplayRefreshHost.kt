package eu.kanade.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Stable
class DisplayRefreshHost {

    internal var currentDisplayRefresh by mutableStateOf(false)

    fun flash() {
        currentDisplayRefresh = true
    }
}

@Composable
fun DisplayRefreshHost(
    hostState: DisplayRefreshHost,
    modifier: Modifier = Modifier,
) {
    val currentDisplayRefresh = hostState.currentDisplayRefresh
    LaunchedEffect(currentDisplayRefresh) {
        if (currentDisplayRefresh) {
            delay(1500)
            hostState.currentDisplayRefresh = false
        }
    }

    if (currentDisplayRefresh) {
        Canvas(
            modifier = modifier.fillMaxSize(),
        ) {
            drawRect(Color.Black)
        }
    }
}
