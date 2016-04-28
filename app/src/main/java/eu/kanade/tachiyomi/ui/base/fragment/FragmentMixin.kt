package eu.kanade.tachiyomi.ui.base.fragment

import android.support.v4.app.FragmentActivity
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity

interface FragmentMixin {

    fun setToolbarTitle(title: String) {
        baseActivity.setToolbarTitle(title)
    }

    fun setToolbarTitle(resourceId: Int) {
        baseActivity.setToolbarTitle(getString(resourceId))
    }

    val baseActivity: BaseActivity
        get() = getActivity() as BaseActivity

    fun getActivity(): FragmentActivity

    fun getString(resource: Int): String
}