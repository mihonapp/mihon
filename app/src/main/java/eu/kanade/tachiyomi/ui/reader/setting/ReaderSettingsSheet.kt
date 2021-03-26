package eu.kanade.tachiyomi.ui.reader.setting

import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.widget.SimpleTabSelectedListener
import eu.kanade.tachiyomi.widget.sheet.TabbedBottomSheetDialog

class ReaderSettingsSheet(private val activity: ReaderActivity) : TabbedBottomSheetDialog(activity) {

    private val generalSettings = ReaderGeneralSettings(activity)
    private val colorFilterSettings = ReaderColorFilterSettings(activity)

    private val sheetBackgroundDim = window?.attributes?.dimAmount ?: 0.25f

    init {
        binding.tabs.addOnTabSelectedListener(object : SimpleTabSelectedListener() {
            // Remove dimmed backdrop so color filter changes can be previewed
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val isFilterTab = tab?.position == 1
                window?.setDimAmount(if (isFilterTab) 0f else sheetBackgroundDim)
                activity.setMenuVisibility(!isFilterTab)
            }
        })
    }

    override fun getTabViews() = listOf(
        generalSettings,
        colorFilterSettings,
    )

    override fun getTabTitles() = listOf(
        R.string.action_settings,
        R.string.custom_filter,
    )
}
