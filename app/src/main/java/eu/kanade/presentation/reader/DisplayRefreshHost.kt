package eu.kanade.presentation.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.milliseconds

@Stable
class DisplayRefreshHost {

    internal var currentDisplayRefresh by mutableStateOf(false)
    private val readerPreferences = Injekt.get<ReaderPreferences>()

    internal val flashMillis = readerPreferences.flashDurationMillis()
    internal val flashMode = readerPreferences.flashColor()

    internal val flashIntervalPref = readerPreferences.flashPageInterval()

    // Internal State for Flash
    private var flashInterval = flashIntervalPref.get()
    private var timesCalled = 0

    fun flash() {
        if (timesCalled % flashInterval == 0) {
            currentDisplayRefresh = true
        }
        timesCalled += 1
    }

    fun setInterval(interval: Int) {
        flashInterval = interval
        timesCalled = 0
    }
}

@Composable
fun DisplayRefreshHost(
    hostState: DisplayRefreshHost,
    modifier: Modifier = Modifier,
) {
    val currentDisplayRefresh = hostState.currentDisplayRefresh
    val refreshDuration by hostState.flashMillis.collectAsState()
    val flashMode by hostState.flashMode.collectAsState()
    val flashInterval by hostState.flashIntervalPref.collectAsState()

    var currentColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(currentDisplayRefresh) {
        if (!currentDisplayRefresh) {
            currentColor = null
            return@LaunchedEffect
        }

        val refreshDurationHalf = refreshDuration.milliseconds / 2
        currentColor = if (flashMode == ReaderPreferences.FlashColor.BLACK) {
            Color.Black
        } else {
            Color.White
        }
        delay(refreshDurationHalf)
        if (flashMode == ReaderPreferences.FlashColor.WHITE_BLACK) {
            currentColor = Color.Black
        }
        delay(refreshDurationHalf)
        hostState.currentDisplayRefresh = false
    }

    LaunchedEffect(flashInterval) {
        hostState.setInterval(flashInterval)
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        currentColor?.let { drawRect(it) }
    }
}
