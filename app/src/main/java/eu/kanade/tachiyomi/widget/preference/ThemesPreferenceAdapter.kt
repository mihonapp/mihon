package eu.kanade.tachiyomi.widget.preference

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PrefThemeItemBinding
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.util.system.getResourceColor
import uy.kohesive.injekt.injectLazy

class ThemesPreferenceAdapter(private val clickListener: OnItemClickListener) :
    RecyclerView.Adapter<ThemesPreferenceAdapter.ThemeViewHolder>() {

    private val preferences: PreferencesHelper by injectLazy()

    private var themes = emptyList<PreferenceValues.AppTheme>()

    private lateinit var binding: PrefThemeItemBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val themeResIds = ThemingDelegate.getThemeResIds(themes[viewType], preferences.themeDarkAmoled().get())
        val themedContext = themeResIds.fold(parent.context) {
                context, themeResId ->
            ContextThemeWrapper(context, themeResId)
        }

        binding = PrefThemeItemBinding.inflate(LayoutInflater.from(themedContext), parent, false)
        return ThemeViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int = position

    override fun getItemCount(): Int = themes.size

    override fun onBindViewHolder(holder: ThemesPreferenceAdapter.ThemeViewHolder, position: Int) {
        holder.bind(themes[position])
    }

    fun setItems(themes: List<PreferenceValues.AppTheme>) {
        this.themes = themes
        notifyDataSetChanged()
    }

    inner class ThemeViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {

        private val selectedColor = view.context.getResourceColor(R.attr.colorAccent)
        private val unselectedColor = view.context.getResourceColor(android.R.attr.divider)

        fun bind(appTheme: PreferenceValues.AppTheme) {
            binding.name.text = view.context.getString(appTheme.titleResId!!)

            // For rounded corners
            binding.badges.clipToOutline = true

            val isSelected = preferences.appTheme().get() == appTheme
            binding.themeCard.isChecked = isSelected
            binding.themeCard.strokeColor = if (isSelected) selectedColor else unselectedColor

            listOf(binding.root, binding.themeCard).forEach {
                it.setOnClickListener {
                    clickListener.onItemClick(bindingAdapterPosition)
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }
}
