package eu.kanade.tachiyomi.ui.base.controller

import com.google.android.material.tabs.TabLayout

interface TabbedController {

    /**
     * @return true to let activity updates tabs visibility (to visible)
     */
    fun configureTabs(tabs: TabLayout): Boolean = true

    fun cleanupTabs(tabs: TabLayout) {}
}
