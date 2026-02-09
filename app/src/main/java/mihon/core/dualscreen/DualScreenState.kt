package mihon.core.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

object DualScreenState {
    /**
     * Controls the content displayed on the secondary screen.
     * Null means the secondary screen is showing the default dashboard.
     */
    private val _activeScreen = MutableStateFlow<Screen?>(null)
    val activeScreen = _activeScreen.asStateFlow()

    /**
     * Events sent from the secondary screen back to the primary activity.
     * Used for navigation that must happen on the main screen context.
     */
    private val _mainScreenEvents = Channel<MainScreenEvent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val mainScreenEvents = _mainScreenEvents.receiveAsFlow()

    private val _rotationEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rotationEvents = _rotationEvents.asSharedFlow()

    val LocalNavigateUp = staticCompositionLocalOf<(() -> Unit)?> { null }

    @Composable
    fun navigateUpOr(fallback: () -> Unit): () -> Unit {
        return LocalNavigateUp.current ?: fallback
    }

    fun openScreen(screen: Screen) {
        _activeScreen.value = screen
    }

    fun openOnMainScreen(screen: Screen) {
        _mainScreenEvents.trySend(MainScreenEvent.OpenScreen(screen))
    }

    fun triggerRotationUpdate() {
        _rotationEvents.tryEmit(Unit)
    }

    fun close() {
        _activeScreen.value = null
    }

    sealed interface MainScreenEvent {
        data class OpenScreen(val screen: Screen) : MainScreenEvent
    }
}