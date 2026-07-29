package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.SpreadShift
import eu.kanade.tachiyomi.ui.reader.setting.SpreadSoloPage
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    // The chapter currently centred on. Its pairing shift ([spreadShifted]) is (re)computed from the
    // session resolver on every rebuild in [setChapters]: a remembered manual shift wins, else a
    // per-series force, else the resolver's decision, else the global default, so it needs no custom setter.
    var currentChapter: ReaderChapter? = null
        private set

    /**
     * Whether spread pairing for [currentChapter] starts with its first page standing alone (so spreads
     * are (2,3), (4,5)...) rather than the default (1,2), (3,4)... Caches the current chapter's shift;
     * recomputed for it on every [setChapters] rebuild. Toggled by [toggleSpreadShift], which also
     * persists the new value.
     */
    var spreadShifted = false
        private set

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext()

    /**
     * Small pool of already-decoded spread halves, keyed by their page. A shift/reload re-pairs the
     * *same* pages, so [destroyView] salvages loaded halves here and [createView] reuses them instead
     * of tearing them down and re-decoding (the expensive part of a re-pairing, since the halves were all
     * decoded a moment earlier in the same window). Insertion-ordered and bounded, so the oldest is
     * evicted (and recycled) first.
     *
     * Reuse is valid only while a half's decode config (crop borders, scale type, …) still matches the
     * live one; a reused half keeps its bitmap, never re-decoding. A config-changing rebuild must
     * therefore not reuse: [PagerViewer.refreshAdapter] runs under [withoutSalvaging]. (Shift/reload
     * keeps the config, so it reuses freely.)
     */
    private val holderPool = LinkedHashMap<ReaderPage, PagerPageHolder>()

    // Off only during a config-changing rebuild (see the pool's reuse invariant above): while false,
    // [destroyView] recycles torn-down halves instead of pooling them, so nothing is reused stale.
    private var salvaging = true

    // The pairing-shift decisions live in the reader session (survive viewer rebuilds), not on this
    // disposable adapter; this adapter only reads them and asks for detection to be run. See
    // [SpreadShiftResolver].
    private val resolver get() = viewer.activity.viewModel.spreadShiftResolver

    private val leftToRight get() = viewer is L2RPagerViewer

    /**
     * The resolver's detected shift for [chapter] in the current direction, or null if not yet settled,
     * the detector abstained, or gutter auto-detect is off; either way [SpreadPairing.resolveShift] then
     * falls through to the per-manga force / global default rather than being pinned to a spurious `false`.
     * Gated on the auto-detect setting even though the scan may have run for "treat wide pages as spreads":
     * that feature uses the scan's wide pages, not its gutter vote.
     */
    private fun decidedShift(chapter: ReaderChapter?): Boolean? {
        if (!viewer.config.spreadAutoDetectOffset) return null
        val id = chapter?.chapter?.id ?: return null
        return (resolver.decisionFor(id, leftToRight) as? SpreadShiftResolver.Decision.Decided)?.shift
    }

    // True for a page the "treat wide pages as spreads" pairing should treat as a two-page spread: on
    // when the setting is enabled and the up-front scan flagged the page wide. Off ⇒ never wide, so the
    // pairing is unchanged. Chapter-aware (each page against its own chapter's scan) so it works across
    // the mixed prev/curr/next item list too.
    private val isWidePage: (ReaderPage) -> Boolean = { page ->
        viewer.config.spreadWidePairing &&
            page.chapter.chapter.id?.let { page.index in resolver.wideIndicesFor(it) } == true
    }

    /**
     * The shift a wide two-page spread pins [chapter] to, the automatic stage of
     * [SpreadPairing.resolveShift], outranking the classifier but yielding to a deliberate user choice
     * (a per-chapter manual toggle or a per-series force), so the manual toggle stays a live override on a
     * wide-governed chapter. Null when "treat wide pages as spreads" is off or the chapter has no interior wide.
     */
    private fun decisiveWideShift(chapter: ReaderChapter?): Boolean? {
        if (!viewer.config.spreadWidePairing) return null
        val pages = chapter?.pages ?: return null
        return SpreadPairing.decisiveShiftFromWides(pages, isWidePage)
    }

    // The (chapterId, shift, source) [logShiftDecision] last emitted; setChapters rebuilds on every page
    // turn, so the decision is logged only when this changes.
    private var lastShiftLog: Triple<Long?, Boolean, String>? = null

    /**
     * Logs the effective pairing shift and which input won it, by the precedence [SpreadPairing.resolveShift]
     * applies: a remembered manual toggle, else a per-series force, else a decisive interior wide, else the
     * classifier, else the global default. Logged once per change so a rebuild doesn't spam; complements the
     * resolver's scan line, which logs the classifier's raw verdict before these overrides apply.
     */
    private fun logShiftDecision(
        chapter: ReaderChapter,
        decisive: Boolean?,
        remembered: Boolean?,
        perSeries: Boolean?,
        classifier: Boolean?,
    ) {
        val source = when {
            remembered != null -> "manual"
            perSeries != null -> "per-series"
            decisive != null -> "wide-spread"
            classifier != null -> "classifier"
            else -> "default"
        }
        val entry = Triple(chapter.chapter.id, spreadShifted, source)
        if (entry == lastShiftLog) return
        lastShiftLog = entry
        logcat { "Spread shift: shifted=$spreadShifted (via $source)" }
    }

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition. A spread-eligible neighbour still being detected
        // is not ready to display (like an unloaded one): withhold its pages and keep the boundary
        // transition, so a crossing holds at the transition until its pairing is decided rather than
        // showing a default pairing that then reflows. Its pages join on the resolver's settle reload.
        if (!awaitingDetection(chapters.prevChapter)) {
            chapters.prevChapter?.pages?.let {
                newItems.addAll(pairPages(it, shifted = shiftForChapter(chapters.prevChapter)))
            }
        }

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters ||
            forceTransition ||
            chapters.prevChapter?.state !is ReaderChapter.State.Loaded ||
            awaitingDetection(chapters.prevChapter)
        ) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        var insertPageLastPage: InsertPage? = null

        // currentChapter and spreadShifted advance together here, before the current and next chapters
        // pair below: spreadShifted caches currentChapter's shift, and shiftForChapter() reuses it for the
        // neighbour that equals currentChapter (resolving the rest), so the two must stay consistent; the
        // previous-chapter preview above pairs first, while both still describe the outgoing chapter.
        // Recompute every rebuild, not just on a chapter change, so a detection that settles after the
        // first layout (a same-chapter reload) is picked up: remembered manual shift, else per-series
        // force, else the resolver, else the global default.
        currentChapter = chapters.currChapter
        val decisiveShift = decisiveWideShift(chapters.currChapter)
        val rememberedShift = SpreadShift.rememberedShift(chapters.currChapter.chapter.spread_shift)
        val perSeriesShift = viewer.activity.viewModel.getMangaSpreadShiftForce()
        val classifierShift = decidedShift(chapters.currChapter)
        val defaultShift = viewer.activity.viewModel.readerPreferences.defaultSpreadShift.get()
        spreadShifted = SpreadPairing.resolveShift(
            remembered = rememberedShift,
            perMangaForce = perSeriesShift,
            decisiveWide = decisiveShift,
            detected = classifierShift,
            default = defaultShift,
        )
        logShiftDecision(chapters.currChapter, decisiveShift, rememberedShift, perSeriesShift, classifierShift)

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }

            newItems.addAll(pairPages(pages, shifted = spreadShifted))
        }

        // Add next chapter transition and pages (withholding the pages of a still-detecting neighbour
        // and keeping its transition, symmetric with the previous-chapter side above).
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (
                    nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded ||
                    awaitingDetection(chapters.nextChapter)
                ) {
                    newItems.add(it)
                }
            }

        if (!awaitingDetection(chapters.nextChapter)) {
            chapters.nextChapter?.pages?.let {
                newItems.addAll(pairPages(it, shifted = shiftForChapter(chapters.nextChapter)))
            }
        }

        // Resets dual-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        if (viewer is R2LPagerViewer) {
            newItems.reverse()
        }

        preprocessed = mutableMapOf()
        items = newItems
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage)
        }

        // Ask the session resolver to decide the current chapter and its preloaded neighbours (once
        // each, session-wide). It reads back through [shiftForChapter] on the next rebuild. The
        // current chapter's layout is held until its decision lands (the viewer's isAwaitingDetection
        // gate), and a neighbour's decision fires a reload that re-pairs its preview before the reader
        // crosses in. So no chapter is ever displayed at a pairing that will then reflow.
        triggerDetection(chapters)
    }

    /**
     * Runs the session resolver's up-front scan for the current chapter and each preloaded neighbour.
     * Runs when gutter auto-detect or "treat wide pages as spreads" is on: the one pass produces both
     * the gutter decision and the wide pages; each feature reads the part it needs.
     */
    private fun triggerDetection(chapters: ViewerChapters) {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return
        if (!viewer.config.spread) return
        if (!viewer.config.spreadAutoDetectOffset && !viewer.config.spreadWidePairing) return
        listOfNotNull(chapters.currChapter, chapters.prevChapter, chapters.nextChapter).forEach {
            if (eligibleForDetect(it)) resolver.ensureDetected(it, viewer.config.spreadWidePairing)
        }
    }

    /**
     * Whether the resolver should scan [chapter]: a local/on-disk source with pages in hand. Remote
     * sources are excluded: their pages load unpredictably, so scanning would stall or reflow mid-read.
     * Gutter auto-detect additionally skips a chapter whose shift the user already fixed; "treat wide
     * pages as spreads" still scans it, since a wide overrides a remembered shift.
     */
    private fun eligibleForDetect(chapter: ReaderChapter): Boolean {
        if (!autoDetectSupportsSource(chapter)) return false
        if (chapter.pages == null) return false
        if (viewer.config.spreadWidePairing) return true
        return SpreadShift.rememberedShift(chapter.chapter.spread_shift) == null
    }

    /**
     * Whether [chapter] is a spread chapter whose scan hasn't settled yet, so its pages are not ready
     * to display and are withheld (decide before display). False when scanning doesn't apply (not a
     * horizontal pager, spread off, neither gutter auto-detect nor wide-pairing on, remote source, or a
     * remembered manual shift with only auto-detect on) or it has already settled.
     */
    private fun awaitingDetection(chapter: ReaderChapter?): Boolean {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return false
        if (!viewer.config.spread) return false
        if (!viewer.config.spreadAutoDetectOffset && !viewer.config.spreadWidePairing) return false
        chapter ?: return false
        if (!eligibleForDetect(chapter)) return false
        val id = chapter.chapter.id ?: return false
        return resolver.decisionFor(id, leftToRight) is SpreadShiftResolver.Decision.Pending
    }

    /**
     * Whether the current chapter is being auto-detected but hasn't settled yet: the signal the
     * viewer uses to hold its first layout (decide before display).
     */
    fun isAwaitingDetection(): Boolean = awaitingDetection(currentChapter)

    /**
     * Pairs up consecutive pages of a single chapter into [PageSpread]s when spread mode is
     * on. Only applies to the horizontal pagers; a no-op otherwise.
     *
     * [shifted] is the run's own pairing shift, supplied per chapter by [setChapters] via
     * [shiftForChapter]: the current chapter's live [spreadShifted] for its own pages, each
     * neighbour's known shift for its preview. This is the other place pairing happens (alongside
     * [regroupChapterAware]), called by every [setChapters] rebuild rather than only an explicit
     * [toggleSpreadShift]. Without honoring the shift here, a rebuild unrelated to pairing (e.g.
     * [ReaderViewModel.preload]'s `Event.ReloadViewerChapters`, which fires as soon as background
     * preloading of the next chapter completes, routine while reading near the end of the current
     * one) would silently revert a chapter to its default pairing, leaving [spreadShifted] (and the
     * bottom-bar icon reflecting it) still showing shifted even though the actual pages no longer are.
     */
    private fun pairPages(pages: List<ReaderPage>, shifted: Boolean = false): List<Any> {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return pages
        if (!viewer.config.spread) return pages
        return SpreadPairing.pairFlat(pages, shifted, isWidePage)
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return items.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        return when (val item = items[position]) {
            is PageSpread -> PagerSpreadHolder(readerThemedContext, viewer, item, reuse = ::reusePooledHalf)
            is ReaderPage -> PagerPageHolder(
                readerThemedContext,
                viewer,
                item,
                soloJustifyIsLeft = soloJustifySideFor(item),
            )
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * When a spread is torn down (e.g. by a shift/reload re-pairing), keep its already-decoded halves
     * in [holderPool] so [createView] can reuse them for their new pairing instead of re-decoding.
     */
    override fun destroyView(container: ViewGroup, position: Int, view: View) {
        if (view is PagerSpreadHolder) {
            view.detach()
            if (salvaging) {
                salvage(view, view.leftHolder)
                salvage(view, view.rightHolder)
            } else {
                view.leftHolder.recycle()
                view.rightHolder.recycle()
            }
        }
    }

    /**
     * Runs [block] with pooling off (halves torn down during it are recycled, not salvaged), so a
     * config-changing rebuild re-decodes rather than reusing a stale bitmap. The adapter reset destroys
     * synchronously, so this only needs to span [block]; re-instantiation then finds an empty pool.
     */
    fun withoutSalvaging(block: () -> Unit) {
        salvaging = false
        try {
            block()
        } finally {
            salvaging = true
        }
    }

    private fun salvage(spread: PagerSpreadHolder, holder: PagerPageHolder) {
        if (!holder.hasLoadedImage) return
        spread.removeView(holder)
        holderPool[holder.page] = holder
        while (holderPool.size > MAX_POOLED_HOLDERS) {
            val eldest = holderPool.keys.first()
            holderPool.remove(eldest)?.recycle()
        }
    }

    /** Frees every pooled half. Called when the viewer is torn down so decoded bitmaps aren't held. */
    fun recyclePool() {
        holderPool.values.forEach { it.recycle() }
        holderPool.clear()
    }

    /**
     * Takes a decoded half from [holderPool] for reuse, enforcing the pool's reuse invariant at the
     * boundary: its decode config must still match the live one. A mismatch means a config-changing
     * rebuild didn't invalidate the pool, a developer warning, since [PagerViewer.refreshAdapter] makes
     * it never fire in normal use; it guards a future rebuild that forgets to.
     */
    private fun reusePooledHalf(page: ReaderPage): PagerPageHolder? {
        val holder = holderPool.remove(page) ?: return null
        if (holder.decodedCropBorders != viewer.config.imageCropBorders) {
            logcat(LogPriority.WARN) {
                "INVARIANT: reusing a spread half decoded with a stale crop-borders setting; a rebuild " +
                    "changed the decode config without invalidating the pool (PagerViewerAdapter.holderPool)"
            }
        }
        return holder
    }

    /**
     * For a top-level (lone) [ReaderPage] in spread mode with [SpreadSoloPage.JUSTIFY], which screen
     * half to justify it to (true = left); null when centring applies (the default), the pager isn't
     * horizontal, or spread mode is off. Uses the page's own chapter's shift ([shiftForChapter]) so a
     * neighbour preview's justified page sits on the same side it will once the chapter is current, and
     * its pairing column (not the raw source index) so it stays aligned with the pairing's parity, wides
     * counted in; see [SpreadPairing.pairingColumnIndex].
     */
    private fun soloJustifySideFor(page: ReaderPage): Boolean? {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return null
        if (!viewer.config.spread || viewer.config.spreadSoloPage != SpreadSoloPage.JUSTIFY) return null
        val column = SpreadPairing.pairingColumnIndex(items, page, isWidePage)
        return SpreadPairing.soloSideIsLeft(column, shiftForChapter(page.chapter), viewer is R2LPagerViewer)
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = items.indexOf(view.item)
            if (position != -1) {
                return position
            } else {
                logcat { "Position for ${view.item} not found" }
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        val currentIndex = items.indexOf(currentPage)

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1
            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (items[placeAtIndex] is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance<InsertPage>()
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    /**
     * Breaks a single spread apart because [widePage] turned out to be a wide image that
     * shouldn't be paired. Local to this spread only; doesn't re-pair the rest of the chapter.
     */
    fun breakSpread(spread: PageSpread) {
        val index = items.indexOf(spread)
        if (index == -1) return
        val (atIndex, atNext) = spreadOrderFor(spread)
        items[index] = atIndex
        items.add(index + 1, atNext)
        notifyDataSetChanged()
    }

    private fun spreadOrderFor(spread: PageSpread): Pair<ReaderPage, ReaderPage> =
        SpreadPairing.spreadOrder(spread, viewer is R2LPagerViewer)

    private fun regroupChapterAware(source: List<Any>): MutableList<Any> =
        SpreadPairing.regroupChapterAware(
            source = source,
            isR2L = viewer is R2LPagerViewer,
            shiftFor = ::shiftForChapter,
            isWide = isWidePage,
        )

    /**
     * The pairing shift known for [chapter] right now. The live [spreadShifted] for the current
     * chapter (which reflects a manual toggle made this session); a stored value for any other; else
     * the session resolver's decision if it has settled (the default while pending). Lets every
     * chapter's run (the current one and each neighbour preview) pair by its own shift, so crossing
     * into a shifted chapter doesn't reshape its opening pairing at the boundary.
     */
    private fun shiftForChapter(chapter: ReaderChapter?): Boolean {
        if (chapter == null) return false
        if (chapter == currentChapter) return spreadShifted
        return SpreadPairing.resolveShift(
            remembered = SpreadShift.rememberedShift(chapter.chapter.spread_shift),
            perMangaForce = viewer.activity.viewModel.getMangaSpreadShiftForce(),
            decisiveWide = decisiveWideShift(chapter),
            detected = decidedShift(chapter),
            default = viewer.activity.viewModel.readerPreferences.defaultSpreadShift.get(),
        )
    }

    /**
     * Toggles whether [currentChapter]'s spreads start pairing from its first page (default) or
     * its second page (leaving the first page standing alone), then reflows the entire chapter
     * to match, not just from wherever the reader currently is. Any trailing unpaired page (odd
     * page count) always renders solo in either state. Ephemeral: resets when [currentChapter]
     * changes. Used to correct scan misalignment (some files start spreads on the "wrong" page).
     */
    fun toggleSpreadShift() {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return
        if (!viewer.config.spread) return
        val chapter = currentChapter ?: return

        spreadShifted = !spreadShifted
        items = regroupChapterAware(items)
        notifyDataSetChanged()

        // Remember the deliberate choice: update the in-memory chapter (so it takes precedence over
        // the resolver on every later rebuild, via [shiftForChapter], with no DB read) and persist
        // it so it survives navigation and restart.
        val choice = if (spreadShifted) SpreadShift.SHIFTED else SpreadShift.UNSHIFTED
        chapter.chapter.spread_shift = choice.flagValue.toLong()
        viewer.activity.viewModel.persistSpreadShift(chapter, choice)
    }

    /**
     * Re-resolves [currentChapter]'s shift and reflows its pairing in place (via
     * [PagerViewer.reapplyShift]'s anchored path, not a raw-index reload, which would walk the reader
     * across the parity flip). No-op when the resolved shift already matches. First clears any per-chapter
     * manual (O-key) override: a per-series choice supersedes the ad-hoc one, which would otherwise shadow
     * it in [SpreadPairing.resolveShift] (remembered ?: perMangaForce ?: …) and make the chip a no-op.
     * Only the current chapter's override is cleared; other chapters keep their explicit per-chapter shift
     * (which still outranks the per-series force by precedence). The asymmetry is deliberate, so the chip
     * takes effect where the reader is looking without silently wiping deliberate corrections elsewhere.
     */
    fun reapplyShift() {
        if (viewer !is L2RPagerViewer && viewer !is R2LPagerViewer) return
        if (!viewer.config.spread) return
        val chapter = currentChapter ?: return
        if (SpreadShift.rememberedShift(chapter.chapter.spread_shift) != null) {
            chapter.chapter.spread_shift = SpreadShift.DEFAULT.flagValue.toLong()
            viewer.activity.viewModel.persistSpreadShift(chapter, SpreadShift.DEFAULT)
        }
        val resolved = SpreadPairing.resolveShift(
            remembered = SpreadShift.rememberedShift(chapter.chapter.spread_shift),
            perMangaForce = viewer.activity.viewModel.getMangaSpreadShiftForce(),
            decisiveWide = decisiveWideShift(chapter),
            detected = decidedShift(chapter),
            default = viewer.activity.viewModel.readerPreferences.defaultSpreadShift.get(),
        )
        if (resolved == spreadShifted) return
        spreadShifted = resolved
        items = regroupChapterAware(items)
        notifyDataSetChanged()
    }

    /**
     * Whether shift auto-detection should run for [chapter]'s source. Restricted to local/on-disk
     * sources: local files, imports, epubs, and downloaded chapters, which have every page readable
     * up front. A streamed remote source loads pages slowly and unpredictably, so detecting there
     * would either stall or, worse, reflow the pairing at some arbitrary moment after the reader has
     * already started reading. Leaving remote browsing untouched is the least-surprising behaviour.
     */
    private fun autoDetectSupportsSource(chapter: ReaderChapter): Boolean = chapter.pageLoader?.isLocal == true

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }
}

// The reuse pool only needs to span the instantiated window (~3 spreads = 6 halves) plus a little
// slack; anything older than that has scrolled well away and is recycled.
private const val MAX_POOLED_HOLDERS = 8
