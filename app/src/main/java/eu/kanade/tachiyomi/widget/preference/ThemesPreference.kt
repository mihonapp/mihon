package eu.kanade.tachiyomi.widget.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues

class ThemesPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ListPreference(context, attrs),
    ThemesPreferenceAdapter.OnItemClickListener {

    private val adapter = ThemesPreferenceAdapter(this)

    init {
        layoutResource = R.layout.pref_themes_list
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val themesList = holder.findViewById(R.id.themes_list) as RecyclerView
        themesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        themesList.adapter = adapter
    }

    override fun onItemClick(position: Int) {
        value = entries[position].name
        callChangeListener(value)
    }

    override fun onClick() {
        // no-op; not actually a DialogPreference
    }

    var entries: List<PreferenceValues.AppTheme> = emptyList()
        set(value) {
            field = value
            adapter.setItems(value)
        }
}
