package tachiyomi.domain.library.model

enum class ChapterNavigationControls(val showArrows: Boolean, val showJumpToTop: Boolean) {
    Disabled(false, false),
    Arrows(true, false),
    JumpToTop(false, true),
    Both(true, true),
}
