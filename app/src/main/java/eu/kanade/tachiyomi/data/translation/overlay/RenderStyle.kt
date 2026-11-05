package eu.kanade.tachiyomi.data.translation.overlay

/** How translated text looks and where it goes (overlay mode). Pure data, so it can be tested without Android. */
data class RenderStyle(
    val font: Font = Font.BOLD,
    /** Share of the largest size that fits the bubble. 100 = as big as fits, smaller values leave more air. */
    val sizePercent: Int = DEFAULT_SIZE_PERCENT,
    val placement: Placement = Placement.REPLACE,
) {

    enum class Font(val id: String) {
        BOLD("bold"),
        REGULAR("regular"),
        SERIF("serif"),
        COMIC("comic"),
        ARABIC_MANGA("arabic_manga"),
        ARABIC_RUQAA("arabic_ruqaa"),
        CUSTOM("custom"),
        ;

        companion object {
            fun fromId(id: String?): Font = entries.firstOrNull { it.id == id } ?: BOLD
        }
    }

    enum class Placement(val id: String) {
        /** Erase the original text and draw the translation in its place. */
        REPLACE("replace"),

        /** Keep the page untouched and put the translation in a caption box next to the original text. */
        BELOW("below"),
        ;

        companion object {
            fun fromId(id: String?): Placement = entries.firstOrNull { it.id == id } ?: REPLACE
        }
    }

    companion object {
        const val DEFAULT_SIZE_PERCENT = 100
        const val MIN_SIZE_PERCENT = 60
        const val MAX_SIZE_PERCENT = 100

        val DEFAULT = RenderStyle()

        fun from(fontId: String?, sizePercent: Int, placementId: String?) = RenderStyle(
            font = Font.fromId(fontId),
            sizePercent = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT),
            placement = Placement.fromId(placementId),
        )
    }
}
