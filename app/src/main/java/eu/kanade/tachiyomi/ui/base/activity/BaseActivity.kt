package eu.kanade.tachiyomi.ui.base.activity

import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity(), ActivityMixin {

    override fun getActivity() = this

}
