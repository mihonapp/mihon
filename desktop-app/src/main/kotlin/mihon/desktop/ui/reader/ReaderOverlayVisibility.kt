package mihon.desktop.ui.reader

internal data class ReaderOverlayVisibilityState(
    val chromeVisible: Boolean = true,
    val cursorVisible: Boolean = true,
    val pointerOverControls: Boolean = false,
)

internal sealed interface ReaderOverlayEvent {
    data object PointerAtEdge : ReaderOverlayEvent
    data object PointerMoved : ReaderOverlayEvent
    data object ReadingInput : ReaderOverlayEvent
    data object ControlEntered : ReaderOverlayEvent
    data object ControlExited : ReaderOverlayEvent
    data object IdleTimeout : ReaderOverlayEvent
}

internal fun reduceReaderOverlayVisibility(
    state: ReaderOverlayVisibilityState,
    event: ReaderOverlayEvent,
): ReaderOverlayVisibilityState = when (event) {
    ReaderOverlayEvent.PointerAtEdge,
    ReaderOverlayEvent.ReadingInput,
    -> state.copy(chromeVisible = true, cursorVisible = true)

    ReaderOverlayEvent.PointerMoved -> state.copy(cursorVisible = true)

    ReaderOverlayEvent.ControlEntered -> state.copy(
        chromeVisible = true,
        cursorVisible = true,
        pointerOverControls = true,
    )

    ReaderOverlayEvent.ControlExited -> state.copy(pointerOverControls = false)
    ReaderOverlayEvent.IdleTimeout -> if (state.pointerOverControls) {
        state
    } else {
        state.copy(chromeVisible = false, cursorVisible = false)
    }
}
