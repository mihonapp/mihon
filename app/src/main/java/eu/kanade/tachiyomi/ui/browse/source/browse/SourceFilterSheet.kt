package eu.kanade.tachiyomi.ui.browse.source.browse

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SourceFilterSheetBinding
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

class SourceFilterSheet(
    activity: Activity,
    onFilterClicked: () -> Unit,
    onResetClicked: () -> Unit
) : BaseBottomSheetDialog(activity) {

    private var filterNavView: FilterNavigationView = FilterNavigationView(activity)
    private val sheetBehavior: BottomSheetBehavior<*>

    init {
        filterNavView.onFilterClicked = {
            onFilterClicked()
            this.dismiss()
        }
        filterNavView.onResetClicked = onResetClicked

        setContentView(filterNavView)

        sheetBehavior = BottomSheetBehavior.from(filterNavView.parent as ViewGroup)
    }

    override fun show() {
        super.show()
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun setFilters(items: List<IFlexible<*>>) {
        filterNavView.adapter.updateDataSet(items)
    }

    class FilterNavigationView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) :
        SimpleNavigationView(context, attrs) {

        var onFilterClicked = {}
        var onResetClicked = {}

        val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
            .setDisplayHeadersAtStartUp(true)

        private val binding = SourceFilterSheetBinding.inflate(
            LayoutInflater.from(context),
            null,
            false
        )

        init {
            recycler.adapter = adapter
            recycler.setHasFixedSize(true)
            (binding.root.getChildAt(1) as ViewGroup).addView(recycler)
            addView(binding.root)
            binding.filterBtn.setOnClickListener { onFilterClicked() }
            binding.resetBtn.setOnClickListener { onResetClicked() }
        }
    }
}
