package eu.kanade.tachiyomi.ui.recent_updates

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible

class RecentChaptersAdapter(val fragment: RecentChaptersFragment) :
        FlexibleAdapter<IFlexible<*>>(null, fragment, true) {

    init {
        setDisplayHeadersAtStartUp(true)
        setStickyHeaders(true)
    }
}