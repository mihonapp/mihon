package mihon.reader.session

import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.SINGLE_LTR,
    val coverOffset: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FIT_WIDTH,
    val zoom: Float = 1f,
) {
    init {
        require(zoom.isFinite()) { "zoom must be finite" }
    }
}

fun interface ReaderMonotonicClock {
    fun nowMillis(): Long
}
