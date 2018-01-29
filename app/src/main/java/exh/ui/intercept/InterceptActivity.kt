package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import kotlinx.android.synthetic.main.eh_activity_intercept.*
import nucleus.factory.RequiresPresenter
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

@RequiresPresenter(InterceptActivityPresenter::class)
class InterceptActivity : BaseRxActivity<InterceptActivityPresenter>() {
    private var statusSubscription: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.eh_activity_intercept)

        //Show back button
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        processLink()
    }

    private fun processLink() {
        if(Intent.ACTION_VIEW == intent.action)
            presenter.loadGallery(intent.dataString)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        statusSubscription?.unsubscribe()
        statusSubscription = presenter.status
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    when(it) {
                        is InterceptResult.Success -> {
                            startActivity(Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_MANGA)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MangaController.MANGA_EXTRA, it.mangaId))
                            onBackPressed()
                        }
                        is InterceptResult.Failure ->
                            MaterialDialog.Builder(this)
                                    .title("Error")
                                    .content("Could not open this gallery:\n\n${it.reason}")
                                    .cancelable(true)
                                    .canceledOnTouchOutside(true)
                                    .positiveText("Ok")
                                    .cancelListener { onBackPressed() }
                                    .dismissListener { onBackPressed() }
                                    .show()
                    }
                }
    }

    override fun onStop() {
        super.onStop()
        statusSubscription?.unsubscribe()
    }
}
