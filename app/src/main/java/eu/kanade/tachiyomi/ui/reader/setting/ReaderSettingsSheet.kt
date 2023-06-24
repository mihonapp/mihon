package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CommonTabbedSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

class ReaderSettingsSheet(
    private val activity: ReaderActivity,
) : BaseBottomSheetDialog(activity) {

    private val tabs = listOf(
        ReaderReadingModeSettings(activity) to R.string.pref_category_reading_mode,
        ReaderGeneralSettings(activity) to R.string.pref_category_general,
    )

    private lateinit var binding: CommonTabbedSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = CommonTabbedSheetBinding.inflate(activity.layoutInflater)

        val adapter = Adapter()
        binding.pager.adapter = adapter
        binding.tabs.setupWithViewPager(binding.pager)

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.25f
    }

    private inner class Adapter : ViewPagerAdapter() {

        override fun createView(container: ViewGroup, position: Int): View {
            return tabs[position].first
        }

        override fun getCount(): Int {
            return tabs.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return activity.resources!!.getString(tabs[position].second)
        }
    }
}
