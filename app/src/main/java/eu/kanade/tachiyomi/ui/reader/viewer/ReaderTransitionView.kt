package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.databinding.ReaderTransitionViewBinding
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlin.math.roundToInt

class ReaderTransitionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val binding: ReaderTransitionViewBinding =
        ReaderTransitionViewBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun bind(transition: ChapterTransition, downloadManager: DownloadManager, manga: Manga?) {
        manga ?: return
        when (transition) {
            is ChapterTransition.Prev -> bindPrevChapterTransition(transition, downloadManager, manga)
            is ChapterTransition.Next -> bindNextChapterTransition(transition, downloadManager, manga)
        }
        missingChapterWarning(transition)
    }

    /**
     * Binds a previous chapter transition on this view and subscribes to the page load status.
     */
    private fun bindPrevChapterTransition(
        transition: ChapterTransition,
        downloadManager: DownloadManager,
        manga: Manga,
    ) {
        val prevChapter = transition.to?.chapter

        binding.lowerText.isVisible = prevChapter != null
        if (prevChapter != null) {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
            val isPrevDownloaded = downloadManager.isChapterDownloaded(
                prevChapter.name,
                prevChapter.scanlator,
                manga.title,
                manga.source,
                skipCache = true,
            )
            val isCurrentDownloaded = transition.from.pageLoader is DownloadPageLoader
            binding.upperText.text = buildSpannedString {
                bold { append(context.getString(R.string.transition_previous)) }
                append("\n${prevChapter.name}")
                if (isPrevDownloaded) addDLImageSpan()
            }
            binding.lowerText.text = buildSpannedString {
                bold { append(context.getString(R.string.transition_current)) }
                append("\n${transition.from.chapter.name}")
                if (isCurrentDownloaded) addDLImageSpan()
            }
        } else {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
            binding.upperText.text = context.getString(R.string.transition_no_previous)
        }
    }

    /**
     * Binds a next chapter transition on this view and subscribes to the load status.
     */
    private fun bindNextChapterTransition(
        transition: ChapterTransition,
        downloadManager: DownloadManager,
        manga: Manga,
    ) {
        val nextChapter = transition.to?.chapter

        binding.lowerText.isVisible = nextChapter != null
        if (nextChapter != null) {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_TEXT_START
            val isCurrentDownloaded = transition.from.pageLoader is DownloadPageLoader
            val isNextDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                manga.title,
                manga.source,
                skipCache = true,
            )
            binding.upperText.text = buildSpannedString {
                bold { append(context.getString(R.string.transition_finished)) }
                append("\n${transition.from.chapter.name}")
                if (isCurrentDownloaded) addDLImageSpan()
            }
            binding.lowerText.text = buildSpannedString {
                bold { append(context.getString(R.string.transition_next)) }
                append("\n${nextChapter.name}")
                if (isNextDownloaded) addDLImageSpan()
            }
        } else {
            binding.upperText.textAlignment = TEXT_ALIGNMENT_CENTER
            binding.upperText.text = context.getString(R.string.transition_no_next)
        }
    }

    private fun SpannableStringBuilder.addDLImageSpan() {
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_offline_pin_24dp)?.mutate()
            ?.apply {
                val size = binding.lowerText.textSize + 4.dpToPx
                setTint(binding.lowerText.currentTextColor)
                setBounds(0, 0, size.roundToInt(), size.roundToInt())
            } ?: return
        append(" ")
        inSpans(ImageSpan(icon)) { append("image") }
    }

    private fun missingChapterWarning(transition: ChapterTransition) {
        if (transition.to == null) {
            binding.warning.isVisible = false
            return
        }

        val hasMissingChapters = when (transition) {
            is ChapterTransition.Prev -> hasMissingChapters(transition.from, transition.to)
            is ChapterTransition.Next -> hasMissingChapters(transition.to, transition.from)
        }

        if (!hasMissingChapters) {
            binding.warning.isVisible = false
            return
        }

        val chapterDifference = when (transition) {
            is ChapterTransition.Prev -> calculateChapterDifference(transition.from, transition.to)
            is ChapterTransition.Next -> calculateChapterDifference(transition.to, transition.from)
        }

        binding.warningText.text = resources.getQuantityString(R.plurals.missing_chapters_warning, chapterDifference.toInt(), chapterDifference.toInt())
        binding.warning.isVisible = true
    }
}
