package eu.kanade.tachiyomi.ui.catalogue.filter

import eu.davidea.flexibleadapter.items.ISectionable
import eu.kanade.tachiyomi.data.source.model.Filter

class TriStateSectionItem(filter: Filter.TriState) : TriStateItem(filter), ISectionable<TriStateItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }
}

class TextSectionItem(filter: Filter.Text) : TextItem(filter), ISectionable<TextItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }
}

class CheckboxSectionItem(filter: Filter.CheckBox) : CheckboxItem(filter), ISectionable<CheckboxItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }
}

class SelectSectionItem(filter: Filter.Select<*>) : SelectItem(filter), ISectionable<SelectItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }
}
