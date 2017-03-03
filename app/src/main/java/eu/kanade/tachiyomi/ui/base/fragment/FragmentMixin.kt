package eu.kanade.tachiyomi.ui.base.fragment

import android.support.v4.app.FragmentActivity
import eu.kanade.tachiyomi.ui.base.activity.ActivityMixin

interface FragmentMixin {

    fun setToolbarTitle(title: String) {
        (getActivity() as ActivityMixin).setToolbarTitle(title)
    }

    fun setToolbarTitle(resourceId: Int) {
        (getActivity() as ActivityMixin).setToolbarTitle(getString(resourceId))
    }

    fun getActivity(): FragmentActivity
    
    fun getString(resource: Int): String
}