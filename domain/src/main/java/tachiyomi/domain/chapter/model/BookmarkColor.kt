package tachiyomi.domain.chapter.model

enum class BookmarkColor(val value: Int) {
    DEFAULT(0),
    RED(1),
    ORANGE(2),
    YELLOW(3),
    GREEN(4),
    BLUE(5),
    PURPLE(6),
    PINK(7),
    ;

    companion object {
        fun from(value: Int): BookmarkColor {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}
