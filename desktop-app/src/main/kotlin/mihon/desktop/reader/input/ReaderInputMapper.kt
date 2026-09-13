package mihon.desktop.reader.input

import mihon.desktop.reader.ReaderWheelBehavior
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReadingMode
import mihon.reader.session.ReaderAction
import kotlin.math.abs

enum class ReaderInputKey {
    LEFT,
    RIGHT,
    A,
    D,
    SPACE,
    PAGE_UP,
    PAGE_DOWN,
    HOME,
    END,
    PLUS,
    MINUS,
    ZERO,
    F,
    F11,
    B,
    ESCAPE,
    X,
}
enum class ReaderInputFocus { READER, TEXT, MENU }
enum class ReaderSideButton { BACK, FORWARD }
data class InputPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "input point must be finite" }
    }
}

data class ReaderInputContext(
    val mode: ReadingMode,
    val wheelBehavior: ReaderWheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION,
    val focus: ReaderInputFocus = ReaderInputFocus.READER,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val pageCount: Int = 1,
    val anchor: ReaderPosition = ReaderPosition(0),
) {
    init {
        require(pageCount > 0) { "page count must be positive" }
    }
}

sealed interface ReaderInputCommand {
    data class Core(val action: ReaderAction) : ReaderInputCommand
    data class ZoomBy(val factor: Float, val centroid: InputPoint) : ReaderInputCommand
    data object PageActions : ReaderInputCommand
    data object Fullscreen : ReaderInputCommand
    data object Borderless : ReaderInputCommand
    data object Escape : ReaderInputCommand
}

data class ReaderInputResult(val action: ReaderInputCommand?) {
    val consumed get() = action != null
}

/** Desktop-only translation layer; Compose/AWT events are adapted at the UI edge. */
class ReaderInputMapper(private val nowMillis: () -> Long = System::currentTimeMillis) {
    private var wheelPixels = 0f
    private var lastPagedWheelAt = Long.MIN_VALUE

    fun mapKey(key: ReaderInputKey, context: ReaderInputContext): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER || context.ctrl) return ReaderInputResult(null)
        val previous = ReaderInputCommand.Core(ReaderAction.Previous)
        val next = ReaderInputCommand.Core(ReaderAction.Next)
        val action = when (key) {
            ReaderInputKey.LEFT, ReaderInputKey.A -> if (context.mode.isRightToLeft) next else previous
            ReaderInputKey.RIGHT, ReaderInputKey.D -> if (context.mode.isRightToLeft) previous else next
            ReaderInputKey.SPACE -> if (context.shift) previous else next
            ReaderInputKey.PAGE_UP -> previous
            ReaderInputKey.PAGE_DOWN -> next
            ReaderInputKey.HOME -> ReaderInputCommand.Core(ReaderAction.SelectPage(0))
            ReaderInputKey.END -> ReaderInputCommand.Core(ReaderAction.SelectPage(context.pageCount - 1))
            ReaderInputKey.PLUS -> ReaderInputCommand.ZoomBy(1.1f, CENTER)
            ReaderInputKey.MINUS -> ReaderInputCommand.ZoomBy(1f / 1.1f, CENTER)
            ReaderInputKey.ZERO -> ReaderInputCommand.ZoomBy(0f, CENTER)
            ReaderInputKey.F -> ReaderInputCommand.Fullscreen
            ReaderInputKey.F11 -> ReaderInputCommand.Fullscreen
            ReaderInputKey.B -> ReaderInputCommand.Borderless
            ReaderInputKey.ESCAPE -> ReaderInputCommand.Escape
            ReaderInputKey.X -> ReaderInputCommand.PageActions
        }
        return ReaderInputResult(action)
    }

    fun mapWheel(
        deltaPixels: Float,
        context: ReaderInputContext,
        ctrl: Boolean = false,
        centroid: InputPoint = CENTER,
    ): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER || deltaPixels == 0f) return ReaderInputResult(null)
        if (ctrl) {
            return ReaderInputResult(
                ReaderInputCommand.ZoomBy(
                    if (deltaPixels >
                        0
                    ) {
                        1.1f
                    } else {
                        1f / 1.1f
                    },
                    centroid,
                ),
            )
        }
        if (context.mode == ReadingMode.VERTICAL || context.mode == ReadingMode.WEBTOON ||
            context.wheelBehavior == ReaderWheelBehavior.SCROLL
        ) {
            val offset = (context.anchor.offsetPixels + deltaPixels.toInt()).coerceAtLeast(0)
            return ReaderInputResult(
                ReaderInputCommand.Core(ReaderAction.SetViewportAnchor(context.anchor.copy(offsetPixels = offset))),
            )
        }
        wheelPixels += deltaPixels
        if (
            abs(wheelPixels) < PAGE_WHEEL_THRESHOLD ||
            (lastPagedWheelAt != Long.MIN_VALUE && nowMillis() - lastPagedWheelAt < PAGE_WHEEL_RATE_LIMIT_MILLIS)
        ) {
            return ReaderInputResult(null)
        }
        val forward = wheelPixels > 0f
        wheelPixels = 0f
        lastPagedWheelAt = nowMillis()
        return ReaderInputResult(ReaderInputCommand.Core(if (forward) ReaderAction.Next else ReaderAction.Previous))
    }

    fun mapSideButton(button: ReaderSideButton, context: ReaderInputContext): ReaderInputResult {
        if (context.focus != ReaderInputFocus.READER) return ReaderInputResult(null)
        val forward = button == ReaderSideButton.FORWARD
        val action = when {
            !context.mode.isRightToLeft -> if (forward) ReaderAction.Next else ReaderAction.Previous
            forward -> ReaderAction.Previous
            else -> ReaderAction.Next
        }
        return ReaderInputResult(ReaderInputCommand.Core(action))
    }

    fun mapPinch(scale: Float, centroid: InputPoint): ReaderInputResult {
        require(scale.isFinite() && scale > 0f) { "pinch scale must be positive and finite" }
        return ReaderInputResult(ReaderInputCommand.ZoomBy(scale, centroid))
    }

    private companion object {
        val CENTER = InputPoint(.5f, .5f)
        const val PAGE_WHEEL_THRESHOLD = 1f
        const val PAGE_WHEEL_RATE_LIMIT_MILLIS = 150L
    }
}
