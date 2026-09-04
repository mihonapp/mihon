package mihon.desktop.window

import kotlin.math.max

data class ScreenBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class WindowPlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
) {
    /** Bounds persisted before entering reader fullscreen or borderless modes. */
    fun normalBounds(): WindowPlacement = copy(maximized = false)

    fun sanitize(screen: ScreenBounds): WindowPlacement {
        val safeWidth = width.coerceIn(MIN_WIDTH.coerceAtMost(screen.width), screen.width)
        val safeHeight = height.coerceIn(MIN_HEIGHT.coerceAtMost(screen.height), screen.height)
        val intersectsScreen = x < screen.x + screen.width &&
            y < screen.y + screen.height &&
            x + safeWidth > screen.x &&
            y + safeHeight > screen.y
        val safeX = if (intersectsScreen) {
            x.coerceIn(screen.x, screen.x + screen.width - safeWidth)
        } else {
            screen.x + max(0, (screen.width - safeWidth) / 2)
        }
        val safeY = if (intersectsScreen) {
            y.coerceIn(screen.y, screen.y + screen.height - safeHeight)
        } else {
            screen.y + max(0, (screen.height - safeHeight) / 2)
        }
        return copy(x = safeX, y = safeY, width = safeWidth, height = safeHeight)
    }

    private companion object {
        const val MIN_WIDTH = 900
        const val MIN_HEIGHT = 600
    }
}
