package eu.kanade.tachiyomi.ui.reader.viewer.book

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.GeneralSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderThemedContent
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerItems
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerPageTracker
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import kotlin.math.abs
import kotlin.math.min

/**
 * A [Viewer] that reads like a book: two facing pages at a time on foldables, tablets and landscape screens, one
 * otherwise, turned over with a page curl that follows the finger. Bound on the left, or on the right when
 * [isRightToLeft], as manga are.
 */
class BookViewer(val activity: ReaderActivity, val isRightToLeft: Boolean) : Viewer {

    private val scope = MainScope()

    internal val settingsViewModel = activity.settingsViewModel
    internal val preferences = settingsViewModel.preferences
    internal val general: GeneralSettings
        get() = settingsViewModel.general.value

    internal val downloadManager by lazy { activity.appGraph.downloadManager }

    /** The reader's night mode rather than the app's, for transitions and errors drawn on the page. */
    internal val readerThemedContext: Context by lazy { activity.createReaderThemeContext() }

    /** Pages and transitions in reading order. */
    internal val items = PagerItems(isReversed = false)

    private val tracker = PagerPageTracker(activity, items, ::setChaptersNow) { _, _ -> }

    internal val images = BookImages(scope)

    /** Whether two pages show at once, which the viewer's size decides. */
    internal var isDouble by mutableStateOf(false)

    internal var firstPageAlone by mutableStateOf(preferences.bookFirstPageAlone.get())

    internal val spreads: List<Spread> by derivedStateOf {
        buildSpreads(items.items, isDouble, firstPageAlone) { page ->
            val size = images.sizes[page]
            size != null && size.width > size.height * WIDE_PAGE_RATIO
        }
    }

    /** An item of the spread shown. It keeps the place when spreads regroup, as when the layout changes. */
    private var anchor: Any? by mutableStateOf(null)

    private var lastIndex = 0

    internal val currentIndex: Int by derivedStateOf {
        val index = spreads.indexOfFirst { anchor in it.items }
        if (index >= 0) {
            lastIndex = index
            index
        } else {
            lastIndex.coerceIn(0, (spreads.size - 1).coerceAtLeast(0))
        }
    }

    /** The turn in progress, if any. */
    internal var turn: Turn? by mutableStateOf(null)

    /** How far over [turn]'s leaf is: 0 showing its first spread, 1 its second. */
    internal var progress by mutableFloatStateOf(0f)

    /** How far the finger moved the turned corner up or down. */
    internal var cornerDy by mutableFloatStateOf(0f)

    /** Whether the leaf is turned from its top corner. */
    internal var fromTop by mutableStateOf(false)

    /** Whether [turn] was started going forward, which a release then favors. */
    private var turningForward = true

    /** Whether a button on a page is held, which turns no pages. */
    internal var buttonPressed by mutableStateOf(false)

    /** Turns asked for by taps and keys, forward when true, played one after another. */
    internal val turnRequests = Channel<Boolean>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_LATEST)

    @Composable
    override fun Content(modifier: Modifier) {
        ReaderThemedContent(readerThemedContext) {
            BookContent(viewer = this, modifier = modifier)
        }
    }

    override fun destroy() {
        super.destroy()
        images.clear()
        scope.cancel()
        turnRequests.close()
    }

    override fun setChapters(chapters: ViewerChapters) {
        tracker.setChapters(chapters)
    }

    private fun setChaptersNow(chapters: ViewerChapters) {
        val forceTransition = general.alwaysShowChapterTransition || anchor is ChapterTransition
        val position = items.items.indexOf(anchor).coerceAtLeast(0)
        items.setChapters(chapters, forceTransition, position)
        if (anchor == null || anchor !in items.items) {
            val pages = chapters.currChapter.pages ?: return
            anchor = pages[min(chapters.currChapter.requestedPage, pages.lastIndex)]
        }
        spreads.getOrNull(currentIndex)?.let(::onSpreadShown)
    }

    override fun moveToPage(page: ReaderPage) {
        if (page !in items.items) return
        anchor = page
        spreads.getOrNull(currentIndex)?.let(::onSpreadShown)
    }

    override fun moveForward() {
        turnRequests.trySend(true)
    }

    override fun moveBackward() {
        turnRequests.trySend(false)
    }

    /**
     * Tells the reader of each item of [spread], last one last, so the reader's place is the furthest page shown and
     * a chapter whose last page shows counts as read.
     */
    internal fun onSpreadShown(spread: Spread) {
        spread.items.forEach { item ->
            val position = items.items.indexOf(item)
            if (position >= 0) tracker.onPageChange(position)
        }
    }

    /**
     * The turn going forward or backward from the spread shown, or null at the end of the book.
     */
    internal fun turnFrom(forward: Boolean): Turn? {
        val index = currentIndex
        return if (forward) {
            Turn(index, index + 1).takeIf { index < spreads.lastIndex }
        } else {
            Turn(index - 1, index).takeIf { index > 0 }
        }
    }

    /**
     * Starts [newTurn] at the spread shown: at 0 going [forward], at 1 coming back.
     */
    internal fun beginTurn(newTurn: Turn, forward: Boolean, top: Boolean) {
        fromTop = top
        cornerDy = 0f
        turningForward = forward
        progress = if (forward) 0f else 1f
        turn = newTurn
        tracker.isIdle = false
    }

    /**
     * Plays a whole turn, as for a tap or a key.
     */
    internal suspend fun playTurn(forward: Boolean) {
        if (turn != null || buttonPressed) return
        val next = turnFrom(forward) ?: return
        if (!general.usePageTransitions) {
            showSpread(if (forward) next.to else next.from)
            return
        }
        beginTurn(next, forward, top = false)
        animate(
            initialValue = progress,
            targetValue = if (forward) 1f else 0f,
            animationSpec = tween(TURN_MILLIS, easing = FastOutSlowInEasing),
        ) { value, _ ->
            progress = value
        }
        finishTurn()
    }

    /**
     * Lets go of the leaf: it falls to whichever side it's headed, given how far over it is and how fast it was
     * flicked, in turns per second. A leaf pulled a little way over finishes its turn, as one does reading, and so
     * does one the finger moved [travel] across a good part of the screen, as a leaf as wide as the screen needs.
     */
    internal suspend fun releaseTurn(velocity: Float, travel: Float = 0f) {
        if (turn == null) return
        val pulled = if (turningForward) progress else 1f - progress
        val completes = when {
            velocity > FLING_VELOCITY -> turningForward
            velocity < -FLING_VELOCITY -> !turningForward
            else -> pulled > TURN_THRESHOLD || travel > TRAVEL_THRESHOLD
        }
        val target = if (completes == turningForward) 1f else 0f
        val duration = (SETTLE_MIN_MILLIS + (TURN_MILLIS - SETTLE_MIN_MILLIS) * abs(target - progress)).toInt()
        coroutineScope {
            launch {
                animate(progress, target, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { value, _ ->
                    progress = value
                }
            }
            launch {
                animate(cornerDy, 0f, animationSpec = tween(duration)) { value, _ -> cornerDy = value }
            }
        }
        finishTurn()
    }

    private fun finishTurn() {
        val finished = turn ?: return
        val destination = if (progress >= 0.5f) finished.to else finished.from
        val moved = destination != currentIndex
        spreads.getOrNull(destination)?.items?.firstOrNull()?.let { anchor = it }
        turn = null
        progress = 0f
        cornerDy = 0f
        tracker.isIdle = true
        if (moved && !activity.isScrollingThroughPages) activity.hideMenu()
    }

    private fun showSpread(index: Int) {
        spreads.getOrNull(index)?.items?.firstOrNull()?.let { anchor = it }
        if (!activity.isScrollingThroughPages) activity.hideMenu()
    }

    internal fun onButtonPressChange(pressed: Boolean) {
        buttonPressed = pressed
    }
}

/**
 * A page turn between neighboring spreads, given by index, [from] before [to] in reading order whichever way it goes.
 */
internal data class Turn(val from: Int, val to: Int)

/** Width over height above which a page is a printed spread, shown alone across both sides. */
private const val WIDE_PAGE_RATIO = 1.05f

private const val TURN_MILLIS = 560
private const val SETTLE_MIN_MILLIS = 140

/** How far over a leaf has to be pulled for letting go to finish the turn. */
private const val TURN_THRESHOLD = 0.3f

/** How far across the screen, of its width, the finger has to move for letting go to finish the turn. */
private const val TRAVEL_THRESHOLD = 0.2f

/** Turns per second past which a flick finishes or undoes a turn however far over the leaf is. */
private const val FLING_VELOCITY = 1.4f
