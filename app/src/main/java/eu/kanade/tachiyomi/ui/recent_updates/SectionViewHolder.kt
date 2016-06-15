package eu.kanade.tachiyomi.ui.recent_updates

import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.view.View
import kotlinx.android.synthetic.main.item_recent_chapter_section.view.*
import java.util.*

class SectionViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {

    /**
     * Current date
     */
    private val now = Date().time

    /**
     * Set value of section header
     *
     * @param date of section header
     */
    fun onSetValues(date: Date) {
        view.section_text.text = DateUtils.getRelativeTimeSpanString(date.time, now, DAY_IN_MILLIS)
    }
}