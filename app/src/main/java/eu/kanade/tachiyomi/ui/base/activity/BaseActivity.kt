package eu.kanade.tachiyomi.ui.base.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.injection.component.AppComponent
import icepick.Icepick
import org.greenrobot.eventbus.EventBus

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        Icepick.restoreInstanceState(this, savedState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
    }

    protected fun setupToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun setToolbarTitle(title: String) {
        supportActionBar?.title = title
    }

    fun setToolbarTitle(titleResource: Int) {
        supportActionBar?.title = getString(titleResource)
    }

    fun setToolbarSubtitle(title: String) {
        supportActionBar?.subtitle = title
    }

    fun setToolbarSubtitle(titleResource: Int) {
        supportActionBar?.subtitle = getString(titleResource)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun registerForEvents() {
        EventBus.getDefault().register(this)
    }

    fun unregisterForEvents() {
        EventBus.getDefault().unregister(this)
    }

    protected val applicationComponent: AppComponent
        get() = App.get(this).component

}