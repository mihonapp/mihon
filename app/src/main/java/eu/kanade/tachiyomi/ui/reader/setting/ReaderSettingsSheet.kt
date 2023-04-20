package eu.kanade.tachiyomi.ui.reader.setting

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CommonTabbedSheetBinding
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import eu.kanade.tachiyomi.widget.listener.SimpleTabSelectedListener
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

class ReaderSettingsSheet(
    private val activity: ReaderActivity,
    private val showColorFilterSettings: Boolean = false,
) : BaseBottomSheetDialog(activity) {

    private val tabs = listOf(
        ReaderReadingModeSettings(activity) to R.string.pref_category_reading_mode,
        ReaderGeneralSettings(activity) to R.string.pref_category_general,
        ReaderColorFilterSettings(activity) to R.string.custom_filter,
    )

    private val backgroundDimAnimator by lazy {
        val sheetBackgroundDim = window?.attributes?.dimAmount ?: 0.25f
        ValueAnimator.ofFloat(sheetBackgroundDim, 0f).also { valueAnimator ->
            valueAnimator.duration = 250
            valueAnimator.addUpdateListener {
                window?.setDimAmount(it.animatedValue as Float)
            }
        }
    }

    private lateinit var binding: CommonTabbedSheetBinding

    override fun createView(inflater: LayoutInflater): View {
        binding = CommonTabbedSheetBinding.inflate(activity.layoutInflater)

        val adapter = Adapter()
        binding.pager.offscreenPageLimit = 2
        binding.pager.adapter = adapter
        binding.tabs.setupWithViewPager(binding.pager)

        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.25f

        val filterTabIndex = tabs.indexOfFirst { it.first is ReaderColorFilterSettings }
        binding.tabs.addOnTabSelectedListener(
            object : SimpleTabSelectedListener() {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val isFilterTab = tab?.position == filterTabIndex

                    // Remove dimmed backdrop so color filter changes can be previewed
                    backgroundDimAnimator.run {
                        if (isFilterTab) {
                            if (animatedFraction < 1f) {
                                start()
                            }
                        } else if (animatedFraction > 0f) {
                            reverse()
                        }
                    }

                    // Hide toolbars
                    if (activity.menuVisible != !isFilterTab) {
                        activity.setMenuVisibility(!isFilterTab)
                    }
                }
            },
        )

        if (showColorFilterSettings) {
            binding.tabs.getTabAt(filterTabIndex)?.select()
        }
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
