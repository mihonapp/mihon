package eu.kanade.tachiyomi.ui.recent_updates

import eu.davidea.flexibleadapter.FlexibleAdapter

class RecentChaptersAdapter(val fragment: RecentChaptersFragment) :
        FlexibleAdapter<RecentChapterItem>(null, fragment, true) {

    init {
        setDisplayHeadersAtStartUp(true)
        setStickyHeaders(true)
    }
}