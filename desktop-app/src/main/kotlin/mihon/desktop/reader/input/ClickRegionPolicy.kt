package mihon.desktop.reader.input

import mihon.desktop.reader.ClickRegions
import mihon.desktop.reader.ReaderClickAction

data class ClickRegion(val start: Float, val end: Float, val action: ReaderClickAction) {
    init {
        require(start.isFinite() && end.isFinite() && start >= 0f && end <= 1f && start < end) {
            "click region must be an ordered normalized fraction"
        }
    }
}

/** Complete, non-overlapping normalized click policy shared by mouse and touch input. */
class ClickRegionPolicy(val regions: List<ClickRegion>) {
    init {
        require(regions.isNotEmpty()) { "click regions must not be empty" }
        require(regions.first().start == 0f && regions.last().end == 1f) { "click regions must cover 0 through 1" }
        regions.zipWithNext().forEach { (left, right) ->
            require(left.end == right.start) { "click regions must not overlap or leave gaps" }
        }
    }

    fun actionAt(horizontalFraction: Float): ReaderClickAction {
        val fraction = horizontalFraction.coerceIn(0f, 1f)
        return regions.firstOrNull { fraction < it.end }?.action ?: regions.last().action
    }

    companion object {
        fun from(settings: ClickRegions) = ClickRegionPolicy(
            listOf(
                ClickRegion(0f, settings.leftEndPercent / 100f, settings.leftAction),
                ClickRegion(settings.leftEndPercent / 100f, settings.centerEndPercent / 100f, settings.centerAction),
                ClickRegion(settings.centerEndPercent / 100f, 1f, settings.rightAction),
            ),
        )

        fun fixedDefaults() = from(ClickRegions())
    }
}
