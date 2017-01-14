package eu.kanade.tachiyomi.ui.catalogue

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.source.model.Filter
import eu.kanade.tachiyomi.data.source.model.FilterList
import eu.kanade.tachiyomi.ui.catalogue.filter.*
import eu.kanade.tachiyomi.util.inflate
import eu.kanade.tachiyomi.widget.SimpleNavigationView
import kotlinx.android.synthetic.main.catalogue_drawer_content.view.*


class CatalogueNavigationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : SimpleNavigationView(context, attrs) {

    val adapter = FlexibleAdapter<IFlexible<*>>(null)

    var onSearchClicked = {}

    var onResetClicked = {}

    init {
        recycler.adapter = adapter
        val view = inflate(R.layout.catalogue_drawer_content)
        ((view as ViewGroup).getChildAt(1) as ViewGroup).addView(recycler)
        addView(view)

        search_btn.setOnClickListener { onSearchClicked() }
        reset_btn.setOnClickListener { onResetClicked() }

        adapter.setDisplayHeadersAtStartUp(true)
        adapter.setStickyHeaders(true)
    }

    fun setFilters(filters: FilterList) {
        val items = filters.mapNotNull {
            when (it) {
                is Filter.Header -> HeaderItem(it)
                is Filter.Separator -> SeparatorItem(it)
                is Filter.CheckBox -> CheckboxItem(it)
                is Filter.TriState -> TriStateItem(it)
                is Filter.Text -> TextItem(it)
                is Filter.Select<*> -> SelectItem(it)
                is Filter.Group<*> -> {
                    val group = GroupItem(it)
                    val subItems = it.state.mapNotNull {
                        when (it) {
                            is Filter.CheckBox -> CheckboxSectionItem(it)
                            is Filter.TriState -> TriStateSectionItem(it)
                            is Filter.Text -> TextSectionItem(it)
                            is Filter.Select<*> -> SelectSectionItem(it)
                            else -> null
                        } as? ISectionable<*, *>
                    }
                    subItems.forEach { it.header = group }
                    group.subItems = subItems
                    group
                }
                is Filter.Sort -> {
                    val group = SortGroup(it)
                    val subItems = it.values.mapNotNull {
                        SortItem(it, group)
                    }
                    group.subItems = subItems
                    group
                }
                else -> null
            }
        }
        adapter.updateDataSet(items)
    }

}