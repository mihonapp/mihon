package eu.kanade.presentation.manga.components

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.presentation.manga.MangaScreenItem
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.launch

@Composable
internal fun rememberChapterScrollNavigation(
    listState: LazyListState,
    topInset: Int,
    volumeKeysEnabled: Boolean,
    enabled: Boolean,
): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    val onPageChange: (Int) -> Unit = remember(listState, topInset) {
        { direction ->
            scope.launch {
                val info = listState.layoutInfo
                val rows = info.visibleItemsInfo.mapNotNull {
                    val type = it.contentType as? MangaScreenItem ?: return@mapNotNull null
                    ChapterNavigationRow(it.offset, it.offset + it.size, type)
                }
                listState.scrollBy(
                    chapterPageDistance(
                        rows,
                        info.viewportStartOffset + topInset,
                        info.viewportEndOffset - info.afterContentPadding,
                        direction,
                        info.mainAxisItemSpacing,
                    ),
                )
            }
        }
    }
    val activity = LocalActivity.current as? MainActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentEnabled by rememberUpdatedState(enabled)
    val currentVolumeKeys by rememberUpdatedState(volumeKeysEnabled)
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    DisposableEffect(activity, lifecycle) {
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            if (!currentEnabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@handler false
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_UP -> -1
                KeyEvent.KEYCODE_PAGE_DOWN -> 1
                KeyEvent.KEYCODE_VOLUME_UP -> if (currentVolumeKeys) -1 else return@handler false
                KeyEvent.KEYCODE_VOLUME_DOWN -> if (currentVolumeKeys) 1 else return@handler false
                else -> return@handler false
            }
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) currentOnPageChange(direction)
            true
        }
        activity?.chapterKeyHandler = handler
        onDispose {
            if (activity?.chapterKeyHandler === handler) activity.chapterKeyHandler = null
        }
    }
    return onPageChange
}

internal data class ChapterNavigationRow(val start: Int, val end: Int, val type: MangaScreenItem)

internal fun chapterPageDistance(
    rows: List<ChapterNavigationRow>,
    viewportStart: Int,
    viewportEnd: Int,
    direction: Int,
    spacing: Int = 0,
): Float {
    val height = (viewportEnd - viewportStart).coerceAtLeast(0)
    val visible = rows.filter { it.end > viewportStart && it.start < viewportEnd }
    if (visible.isEmpty() || height == 0) return 0f
    val hasMetadata = visible.any { it.type != MangaScreenItem.CHAPTER && it.type != MangaScreenItem.CHAPTER_HEADER }
    if (hasMetadata) {
        val section = visible.firstOrNull {
            it.type == MangaScreenItem.CHAPTER_HEADER || it.type == MangaScreenItem.CHAPTER
        }
        if (direction > 0 && section != null) return (section.start - viewportStart).coerceAtLeast(0).toFloat()
        return direction * height * 0.7f
    }
    val whole = visible.filter {
        it.type == MangaScreenItem.CHAPTER && it.start >= viewportStart && it.end <= viewportEnd
    }
    val distance = if (whole.isNotEmpty()) {
        (whole.last().end - whole.first().start + spacing).toFloat()
    } else {
        height * 0.7f
    }
    return direction * distance
}
