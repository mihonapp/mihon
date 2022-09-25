package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.os.Bundle
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import nucleus.view.NucleusAppCompatActivity
import uy.kohesive.injekt.injectLazy

open class BaseRxActivity<P : BasePresenter<*>> :
    NucleusAppCompatActivity<P>(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    protected val preferences: BasePreferences by injectLazy()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }
}
