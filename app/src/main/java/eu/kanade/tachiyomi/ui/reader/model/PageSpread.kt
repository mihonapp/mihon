package eu.kanade.tachiyomi.ui.reader.model

/**
 * Two pages shown side by side as a single spread.
 *
 * Equality is by the two wrapped pages, not instance identity: the adapter constructs fresh spreads on
 * every chapter-window rebuild, while the [ReaderPage]s are stable across rebuilds. By-value equality lets
 * the pager's `getItemPosition` locate a still-onscreen spread in the rebuilt list and keep its view and
 * position instead of recreating it. Same reason [ChapterTransition] overrides equals.
 */
class PageSpread(val firstPage: ReaderPage, val secondPage: ReaderPage) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageSpread) return false
        return firstPage == other.firstPage && secondPage == other.secondPage
    }

    override fun hashCode(): Int {
        return 31 * firstPage.hashCode() + secondPage.hashCode()
    }
}
