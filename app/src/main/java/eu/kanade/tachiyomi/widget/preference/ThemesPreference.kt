package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.util.system.dpToPx

class ThemesPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ListPreference(context, attrs),
    ThemesPreferenceAdapter.OnItemClickListener {

    private var recycler: RecyclerView? = null
    private val adapter = ThemesPreferenceAdapter(this)

    var lastScrollPosition: Int? = null

    var entries: List<PreferenceValues.AppTheme> = emptyList()
        set(value) {
            field = value
            adapter.setItems(value)
        }

    init {
        layoutResource = R.layout.pref_themes_list
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        recycler = holder.findViewById(R.id.themes_list) as RecyclerView
        recycler?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recycler?.adapter = adapter

        // Retain scroll position on activity recreate after changing theme
        recycler?.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    lastScrollPosition = recyclerView.computeHorizontalScrollOffset()
                }
            },
        )
        lastScrollPosition?.let { scrollToOffset(it) }
    }

    override fun onItemClick(position: Int) {
        if (position !in 0..entries.size) {
            return
        }

        callChangeListener(value)
        value = entries[position].name
    }

    override fun onClick() {
        // no-op; not actually a DialogPreference
    }

    private fun scrollToOffset(lX: Int) {
        recycler?.let {
            (it.layoutManager as LinearLayoutManager).apply {
                scrollToPositionWithOffset(
                    // 114dp is the width of the pref_theme_item layout
                    lX / 114.dpToPx,
                    -lX % 114.dpToPx,
                )
            }
            lastScrollPosition = it.computeHorizontalScrollOffset()
        }
    }
}
