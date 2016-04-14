package eu.kanade.tachiyomi.ui.base.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.TextView
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.R

open class BaseActivity : AppCompatActivity() {

    protected fun setupToolbar(toolbar: Toolbar, backNavigation: Boolean = true) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (backNavigation) {
            toolbar.setNavigationOnClickListener { onBackPressed() }
        }
    }

    fun setAppTheme() {
        when (app.appTheme) {
            2 -> setTheme(R.style.Theme_Tachiyomi_Dark)
            else -> setTheme(R.style.Theme_Tachiyomi)
        }
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

    /**
     * Requests read and write permissions on Android M and higher.
     */
    fun requestPermissionsOnMarshmallow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                        1)

            }
        }
    }

    protected val app: App
        get() = App.get(this)

    inline fun View.snack(message: String, length: Int = Snackbar.LENGTH_LONG, f: Snackbar.() -> Unit) {
        val snack = Snackbar.make(this, message, length)
        val textView = snack.view.findViewById(android.support.design.R.id.snackbar_text) as TextView
        textView.setTextColor(Color.WHITE)
        snack.f()
        snack.show()
    }

}