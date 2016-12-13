package eu.kanade.tachiyomi.ui.base.activity

import android.support.v7.app.AppCompatActivity
import eu.kanade.tachiyomi.util.LocaleHelper

abstract class BaseActivity : AppCompatActivity(), ActivityMixin {

    init {
        LocaleHelper.updateCfg(this)
    }

    override fun getActivity() = this

}
