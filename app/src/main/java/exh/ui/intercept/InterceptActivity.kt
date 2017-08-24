package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.GalleryAddEvent
import exh.GalleryAdder
import kotlinx.android.synthetic.main.eh_activity_intercept.*
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

class InterceptActivity : BaseActivity() {

    private val preferences: PreferencesHelper by injectLazy()

    private val galleryAdder = GalleryAdder()

    var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        //Set theme
        setTheme(when (preferences.theme()) {
            2 -> R.style.Theme_Tachiyomi_Dark
            3 -> R.style.Theme_Tachiyomi_Amoled
            else -> R.style.Theme_Tachiyomi
        })

        super.onCreate(savedInstanceState)
        setContentView(R.layout.eh_activity_intercept)

        //Show back button
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if(savedInstanceState == null)
            thread { processLink() }
    }

    private fun processLink() {
        if(Intent.ACTION_VIEW == intent.action) {
            val result = galleryAdder.addGallery(intent.dataString)

            when(result) {
                is GalleryAddEvent.Success ->
                    if(!finished)
                        startActivity(Intent(this, MainActivity::class.java)
                                .setAction(MainActivity.SHORTCUT_MANGA)
                                .putExtra(MangaController.MANGA_EXTRA, result.manga.id))
                is GalleryAddEvent.Fail ->
                    if(!finished)
                        runOnUiThread {
                            MaterialDialog.Builder(this)
                                    .title("Error")
                                    .content("Could not open this gallery:\n\n${result.logMessage}")
                                    .cancelable(true)
                                    .canceledOnTouchOutside(true)
                                    .cancelListener { onBackPressed() }
                                    .positiveText("Ok")
                                    .onPositive { _, _ -> onBackPressed() }
                                    .dismissListener { onBackPressed() }
                                    .show()
                        }
            }
            onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onBackPressed() {
        if(!finished)
            runOnUiThread {
                super.onBackPressed()
            }
    }

    override fun onStop() {
        super.onStop()
        finished = true
    }
}
