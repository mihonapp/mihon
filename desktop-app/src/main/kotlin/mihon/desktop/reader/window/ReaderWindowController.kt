package mihon.desktop.reader.window

import mihon.desktop.reader.ReaderWindowMode
import mihon.desktop.window.WindowPlacement

enum class ReaderWindowEscape { ReturnedToNormal, CloseReader }

/** Serializes reader-window state changes without owning an AWT or Compose window. */
class ReaderWindowController(
    initialNormalBounds: WindowPlacement,
    private val applyMode: (ReaderWindowMode) -> Unit = {},
) {
    var mode: ReaderWindowMode = ReaderWindowMode.NORMAL
        private set
    var normalBounds: WindowPlacement = initialNormalBounds.normalBounds()
        private set

    @Synchronized
    fun restore(mode: ReaderWindowMode, normalBounds: WindowPlacement) {
        this.normalBounds = normalBounds.normalBounds()
        this.mode = mode
    }

    @Synchronized
    fun transition(target: ReaderWindowMode, currentBounds: WindowPlacement): ReaderWindowMode {
        if (mode == ReaderWindowMode.NORMAL &&
            target != ReaderWindowMode.NORMAL
        ) {
            normalBounds = currentBounds.normalBounds()
        }
        mode = target
        applyMode(target)
        return mode
    }

    fun toggleFullscreen(currentBounds: WindowPlacement) = transition(
        if (mode == ReaderWindowMode.FULLSCREEN) ReaderWindowMode.NORMAL else ReaderWindowMode.FULLSCREEN,
        currentBounds,
    )

    fun toggleBorderless(currentBounds: WindowPlacement) = transition(
        if (mode == ReaderWindowMode.BORDERLESS) ReaderWindowMode.NORMAL else ReaderWindowMode.BORDERLESS,
        currentBounds,
    )

    @Synchronized
    fun onEscape(currentBounds: WindowPlacement): ReaderWindowEscape = if (mode != ReaderWindowMode.NORMAL) {
        transition(ReaderWindowMode.NORMAL, currentBounds)
        ReaderWindowEscape.ReturnedToNormal
    } else {
        ReaderWindowEscape.CloseReader
    }
}
