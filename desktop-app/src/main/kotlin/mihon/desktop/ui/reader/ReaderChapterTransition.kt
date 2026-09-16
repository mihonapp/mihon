package mihon.desktop.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.reader.DesktopReaderSettings

enum class ReaderChapterTransitionDirection {
    PREVIOUS,
    NEXT,
}

/** Chapter information used by the desktop transition surface. */
data class ReaderChapterTransitionChapter(
    val id: Long? = null,
    val title: String,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val downloaded: Boolean = false,
    val available: Boolean = true,
    val read: Boolean = false,
    val filtered: Boolean = false,
    val duplicate: Boolean = false,
) {
    val statusLabel: String
        get() = when {
            !available -> "Missing"
            downloaded -> "Downloaded"
            else -> "Not downloaded"
        }

    fun skipReasons(settings: DesktopReaderSettings): List<String> = buildList {
        if (settings.skipReadChapters && read) add("read")
        if (settings.skipFilteredChapters && filtered) add("filtered")
        if (settings.skipDuplicateChapters && duplicate) add("duplicate")
    }

    fun isSkipped(settings: DesktopReaderSettings): Boolean = skipReasons(settings).isNotEmpty()
}

data class ReaderChapterTransition(
    val direction: ReaderChapterTransitionDirection,
    val previous: ReaderChapterTransitionChapter?,
    val current: ReaderChapterTransitionChapter,
    val next: ReaderChapterTransitionChapter?,
    val target: ReaderChapterTransitionChapter?,
)

/**
 * Picks the adjacent chapter to navigate to, honoring the desktop skip settings. Candidates are
 * ordered in reading order and the nearest non-skipped neighbor wins.
 */
fun selectChapterTransitionTarget(
    chapters: List<ReaderChapterTransitionChapter>,
    currentIndex: Int,
    direction: ReaderChapterTransitionDirection,
    settings: DesktopReaderSettings = DesktopReaderSettings(),
): ReaderChapterTransitionChapter? {
    if (currentIndex !in chapters.indices) return null
    val step = if (direction == ReaderChapterTransitionDirection.NEXT) 1 else -1
    var index = currentIndex + step
    while (index in chapters.indices) {
        val candidate = chapters[index]
        if (!candidate.isSkipped(settings)) return candidate
        index += step
    }
    return null
}

/**
 * Builds the transition model. When [chapters] contains the current chapter it wins over the
 * explicit [previous]/[next] fallbacks, which lets callers without a catalog still show the
 * current chapter and the available boundary state.
 */
fun readerChapterTransition(
    direction: ReaderChapterTransitionDirection,
    current: ReaderChapterTransitionChapter,
    chapters: List<ReaderChapterTransitionChapter> = emptyList(),
    previous: ReaderChapterTransitionChapter? = null,
    next: ReaderChapterTransitionChapter? = null,
    settings: DesktopReaderSettings = DesktopReaderSettings(),
): ReaderChapterTransition {
    val currentIndex = current.id?.let { id -> chapters.indexOfFirst { it.id == id } } ?: -1
    val resolvedPrevious = if (currentIndex >= 0) chapters.getOrNull(currentIndex - 1) else previous
    val resolvedNext = if (currentIndex >= 0) chapters.getOrNull(currentIndex + 1) else next
    val fallbackTarget = when (direction) {
        ReaderChapterTransitionDirection.PREVIOUS -> resolvedPrevious
        ReaderChapterTransitionDirection.NEXT -> resolvedNext
    }
    val target = when {
        currentIndex >= 0 -> selectChapterTransitionTarget(chapters, currentIndex, direction, settings)
        fallbackTarget?.isSkipped(settings) == true -> null
        else -> fallbackTarget
    }
    return ReaderChapterTransition(
        direction = direction,
        previous = resolvedPrevious,
        current = current,
        next = resolvedNext,
        target = target,
    )
}

@Composable
fun ReaderChapterTransitionSurface(
    transition: ReaderChapterTransition,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    settings: DesktopReaderSettings = DesktopReaderSettings(),
    allowDismiss: Boolean = true,
) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .testTag("reader-chapter-transition"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(24.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = strings.text(UiText.ChapterTransition),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = when (transition.direction) {
                        ReaderChapterTransitionDirection.PREVIOUS -> strings.readerPreviousChapter
                        ReaderChapterTransitionDirection.NEXT -> strings.readerNextChapter
                    },
                    modifier = Modifier.testTag("reader-transition-direction"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TransitionChapterRow(
                    label = strings.text(UiText.Previous),
                    chapter = transition.previous,
                    missingText = strings.text(UiText.NoPreviousChapter),
                    tag = "reader-transition-previous",
                    settings = settings,
                )
                TransitionChapterRow(
                    label = strings.text(UiText.Current),
                    chapter = transition.current,
                    missingText = strings.text(UiText.CurrentChapterUnavailable),
                    tag = "reader-transition-current",
                    settings = settings,
                )
                TransitionChapterRow(
                    label = strings.text(UiText.Next),
                    chapter = transition.next,
                    missingText = strings.text(UiText.NoNextChapter),
                    tag = "reader-transition-next",
                    settings = settings,
                )
                Text(
                    text = transitionTargetLabel(transition, strings),
                    modifier = Modifier.testTag("reader-transition-target"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (allowDismiss) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("reader-transition-dismiss"),
                        ) {
                            Text(strings.text(UiText.StayHere))
                        }
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.testTag("reader-transition-continue"),
                    ) {
                        Text(if (transition.target != null) strings.text(UiText.Continue) else strings.dialogClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitionChapterRow(
    label: String,
    chapter: ReaderChapterTransitionChapter?,
    missingText: String,
    tag: String,
    settings: DesktopReaderSettings,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (chapter == null) {
            Text(
                text = missingText,
                modifier = Modifier.testTag("$tag-missing"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = chapter.title,
                modifier = Modifier.testTag("$tag-title"),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.text(
                        when {
                            !chapter.available -> UiText.Missing
                            chapter.downloaded -> UiText.Downloaded
                            else -> UiText.NotDownloaded
                        },
                    ),
                    modifier = Modifier.testTag("$tag-status"),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !chapter.available -> MaterialTheme.colorScheme.error
                        chapter.downloaded -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                chapter.chapterNumber?.let { number ->
                    Text(
                        text = strings.text(UiText.ChapterNumberLabel, formatChapterNumber(number)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let { scanlator ->
                    Text(
                        text = scanlator,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val skipReasons = chapter.skipReasons(settings).map { reason ->
                strings.text(
                    when (reason) {
                        "read" -> UiText.ReadReason
                        "filtered" -> UiText.FilteredReason
                        else -> UiText.DuplicateReason
                    },
                )
            }
            if (skipReasons.isNotEmpty()) {
                Text(
                    text = strings.text(UiText.Skipped, skipReasons.joinToString(", ")),
                    modifier = Modifier.testTag("$tag-skipped"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun transitionTargetLabel(transition: ReaderChapterTransition, strings: DesktopStrings): String {
    val target = transition.target
    return when {
        target == null && transition.direction == ReaderChapterTransitionDirection.NEXT ->
            strings.text(UiText.NoNextAvailable)
        target == null -> strings.text(UiText.NoPreviousAvailable)
        transition.direction == ReaderChapterTransitionDirection.NEXT &&
            transition.next?.id != null &&
            target.id != transition.next.id -> strings.text(UiText.SkippingTo, target.title)
        transition.direction == ReaderChapterTransitionDirection.PREVIOUS &&
            transition.previous?.id != null &&
            target.id != transition.previous.id -> strings.text(UiText.SkippingTo, target.title)
        else -> strings.text(UiText.ContinuingTo, target.title)
    }
}

private fun formatChapterNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else "%.1f".format(number)
