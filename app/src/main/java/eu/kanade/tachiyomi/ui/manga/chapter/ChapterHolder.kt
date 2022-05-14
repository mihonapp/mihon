package eu.kanade.tachiyomi.ui.manga.chapter

import android.text.SpannableStringBuilder
import android.view.View
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.ChaptersItemBinding
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.manga.chapter.base.BaseChapterHolder
import eu.kanade.tachiyomi.util.lang.toRelativeString
import java.util.Date

class ChapterHolder(
    view: View,
    private val adapter: ChaptersAdapter,
) : BaseChapterHolder(view, adapter) {

    private val binding = ChaptersItemBinding.bind(view)

    init {
        binding.download.setOnClickListener {
            onDownloadClick(it, bindingAdapterPosition)
        }
        binding.download.setOnLongClickListener {
            onDownloadLongClick(bindingAdapterPosition)
            true
        }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter

        binding.chapterTitle.text = when (manga.displayMode) {
            Manga.CHAPTER_DISPLAY_NUMBER -> {
                val number = adapter.decimalFormat.format(chapter.chapter_number.toDouble())
                itemView.context.getString(R.string.display_mode_chapter, number)
            }
            else -> chapter.name
            // TODO: show cleaned name consistently around the app
            // else -> cleanChapterName(chapter, manga)
        }

        // Set correct text color
        val chapterTitleColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColor
        }
        binding.chapterTitle.setTextColor(chapterTitleColor)

        val chapterDescriptionColor = when {
            chapter.read -> adapter.readColor
            chapter.bookmark -> adapter.bookmarkedColor
            else -> adapter.unreadColorSecondary
        }
        binding.chapterDescription.setTextColor(chapterDescriptionColor)

        binding.bookmarkIcon.isVisible = chapter.bookmark

        val descriptions = mutableListOf<CharSequence>()

        if (chapter.date_upload > 0) {
            descriptions.add(Date(chapter.date_upload).toRelativeString(itemView.context, adapter.relativeTime, adapter.dateFormat))
        }
        if (!chapter.read && chapter.last_page_read > 0) {
            val lastPageRead = buildSpannedString {
                color(adapter.readColor) {
                    append(itemView.context.getString(R.string.chapter_progress, chapter.last_page_read + 1))
                }
            }
            descriptions.add(lastPageRead)
        }
        if (!chapter.scanlator.isNullOrBlank()) {
            descriptions.add(chapter.scanlator!!)
        }

        if (descriptions.isNotEmpty()) {
            binding.chapterDescription.text = descriptions.joinTo(SpannableStringBuilder(), " • ")
        } else {
            binding.chapterDescription.text = ""
        }

        binding.download.isVisible = item.manga.source != LocalSource.ID
        binding.download.setState(item.status, item.progress)
    }

    private fun cleanChapterName(chapter: Chapter, manga: Manga): String {
        return chapter.name
            .trim()
            .removePrefix(manga.title)
            .trim(*CHAPTER_TRIM_CHARS)
    }
}

private val CHAPTER_TRIM_CHARS = arrayOf(
    // Whitespace
    ' ',
    '\u0009',
    '\u000A',
    '\u000B',
    '\u000C',
    '\u000D',
    '\u0020',
    '\u0085',
    '\u00A0',
    '\u1680',
    '\u2000',
    '\u2001',
    '\u2002',
    '\u2003',
    '\u2004',
    '\u2005',
    '\u2006',
    '\u2007',
    '\u2008',
    '\u2009',
    '\u200A',
    '\u2028',
    '\u2029',
    '\u202F',
    '\u205F',
    '\u3000',

    // Separators
    '-',
    '_',
    ',',
    ':',
).toCharArray()
