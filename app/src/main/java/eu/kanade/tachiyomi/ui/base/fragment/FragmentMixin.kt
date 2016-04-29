package eu.kanade.tachiyomi.ui.base.fragment

import android.support.v7.app.AppCompatActivity
import eu.kanade.tachiyomi.ui.base.activity.ActivityMixin

interface FragmentMixin {

    fun setToolbarTitle(title: String) {
        (getActivity() as ActivityMixin).setToolbarTitle(title)
    }

    fun setToolbarTitle(resourceId: Int) {
        (getActivity() as ActivityMixin).setToolbarTitle(getString(resourceId))
    }

    fun getActivity(): AppCompatActivity
    
    fun getString(resource: Int): String
}