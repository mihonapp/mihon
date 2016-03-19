package eu.kanade.tachiyomi.ui.base.fragment

import android.support.v4.app.Fragment
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import org.greenrobot.eventbus.EventBus

open class BaseFragment : Fragment() {

    fun setToolbarTitle(title: String) {
        baseActivity.setToolbarTitle(title)
    }

    fun setToolbarTitle(resourceId: Int) {
        baseActivity.setToolbarTitle(getString(resourceId))
    }

    val baseActivity: BaseActivity
        get() = activity as BaseActivity

    fun registerForEvents() {
        EventBus.getDefault().register(this)
    }

    fun unregisterForEvents() {
        EventBus.getDefault().unregister(this)
    }

}
