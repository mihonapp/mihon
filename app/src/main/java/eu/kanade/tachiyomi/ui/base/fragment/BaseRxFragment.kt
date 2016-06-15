package eu.kanade.tachiyomi.ui.base.fragment

import android.os.Bundle
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import nucleus.view.NucleusSupportFragment

abstract class BaseRxFragment<P : BasePresenter<*>> : NucleusSupportFragment<P>(), FragmentMixin {

    override fun onCreate(savedState: Bundle?) {
        val superFactory = presenterFactory
        setPresenterFactory {
            superFactory.createPresenter().apply {
                val app = activity.application as App
                context = app.applicationContext
            }
        }
        super.onCreate(savedState)
    }
}
