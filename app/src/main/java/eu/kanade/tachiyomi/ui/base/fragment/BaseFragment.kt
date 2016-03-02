package eu.kanade.tachiyomi.ui.base.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import icepick.Icepick
import org.greenrobot.eventbus.EventBus

open class BaseFragment : Fragment() {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        Icepick.restoreInstanceState(this, savedState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

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
