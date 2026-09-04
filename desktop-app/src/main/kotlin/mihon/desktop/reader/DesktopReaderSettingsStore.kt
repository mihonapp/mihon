package mihon.desktop.reader

import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode
import mihon.reader.session.ReaderSettings

/** Versioned desktop-only reader preferences, kept separate from the portable reader-core API. */
data class DesktopReaderSettings(
    val mode: ReadingMode = ReadingMode.SINGLE_LTR,
    val coverOffset: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FIT_WIDTH,
    val clickRegions: ClickRegions = ClickRegions(),
    val wheelBehavior: ReaderWheelBehavior = ReaderWheelBehavior.PAGE_NAVIGATION,
    val lastWindowMode: ReaderWindowMode = ReaderWindowMode.NORMAL,
) {
    fun toCoreSettings(): ReaderSettings = ReaderSettings(mode, coverOffset, scaleMode)
}

data class ClickRegions(
    val leftAction: ReaderClickAction = ReaderClickAction.PREVIOUS,
    val centerAction: ReaderClickAction = ReaderClickAction.TOGGLE_CHROME,
    val rightAction: ReaderClickAction = ReaderClickAction.NEXT,
    val leftEndPercent: Int = 25,
    val centerEndPercent: Int = 75,
) {
    init {
        require(leftEndPercent in 1 until centerEndPercent) { "left click region must precede the center region" }
        require(centerEndPercent in 2..99) { "center click region must end before 100" }
    }
}

enum class ReaderClickAction { PREVIOUS, TOGGLE_CHROME, NEXT, NONE }

enum class ReaderWheelBehavior { PAGE_NAVIGATION, SCROLL }

enum class ReaderWindowMode { NORMAL, MAXIMIZED }

class DesktopReaderSettingsStore(private val preferences: DesktopPreferenceStore) {

    fun load(): DesktopReaderSettings {
        val defaults = DesktopReaderSettings()
        val leftEnd = preferences.property(LEFT_END)?.toIntOrNull()
        val centerEnd = preferences.property(CENTER_END)?.toIntOrNull()
        val clickRegions = runCatching {
            ClickRegions(
                leftAction = enumOrDefault(preferences.property(LEFT_ACTION), defaults.clickRegions.leftAction),
                centerAction = enumOrDefault(preferences.property(CENTER_ACTION), defaults.clickRegions.centerAction),
                rightAction = enumOrDefault(preferences.property(RIGHT_ACTION), defaults.clickRegions.rightAction),
                leftEndPercent = leftEnd ?: defaults.clickRegions.leftEndPercent,
                centerEndPercent = centerEnd ?: defaults.clickRegions.centerEndPercent,
            )
        }.getOrDefault(defaults.clickRegions)
        return DesktopReaderSettings(
            mode = enumOrDefault(preferences.property(MODE), defaults.mode),
            coverOffset = preferences.property(COVER_OFFSET)?.toBooleanStrictOrNull() ?: defaults.coverOffset,
            scaleMode = enumOrDefault(preferences.property(SCALE), defaults.scaleMode),
            clickRegions = clickRegions,
            wheelBehavior = enumOrDefault(preferences.property(WHEEL), defaults.wheelBehavior),
            lastWindowMode = enumOrDefault(preferences.property(WINDOW), defaults.lastWindowMode),
        )
    }

    fun save(settings: DesktopReaderSettings) {
        preferences.update {
            setProperty(MODE, settings.mode.name)
            setProperty(COVER_OFFSET, settings.coverOffset.toString())
            setProperty(SCALE, settings.scaleMode.name)
            setProperty(LEFT_ACTION, settings.clickRegions.leftAction.name)
            setProperty(CENTER_ACTION, settings.clickRegions.centerAction.name)
            setProperty(RIGHT_ACTION, settings.clickRegions.rightAction.name)
            setProperty(LEFT_END, settings.clickRegions.leftEndPercent.toString())
            setProperty(CENTER_END, settings.clickRegions.centerEndPercent.toString())
            setProperty(WHEEL, settings.wheelBehavior.name)
            setProperty(WINDOW, settings.lastWindowMode.name)
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private companion object {
        const val MODE = "reader.v1.mode"
        const val COVER_OFFSET = "reader.v1.cover-offset"
        const val SCALE = "reader.v1.scale"
        const val LEFT_ACTION = "reader.v1.click.left-action"
        const val CENTER_ACTION = "reader.v1.click.center-action"
        const val RIGHT_ACTION = "reader.v1.click.right-action"
        const val LEFT_END = "reader.v1.click.left-end"
        const val CENTER_END = "reader.v1.click.center-end"
        const val WHEEL = "reader.v1.wheel"
        const val WINDOW = "reader.v1.window"
    }
}
