package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhActivityInterceptBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import nucleus.factory.RequiresPresenter
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

@RequiresPresenter(InterceptActivityPresenter::class)
class InterceptActivity : BaseRxActivity<EhActivityInterceptBinding, InterceptActivityPresenter>() {
    private var statusSubscription: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EhActivityInterceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        processLink()
    }

    private fun processLink() {
        if (Intent.ACTION_VIEW == intent.action) {
            binding.interceptProgress.visible()
            binding.interceptStatus.text = "Loading gallery..."
            presenter.loadGallery(intent.dataString)
        }
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
                    when (it) {
                        is InterceptResult.Success -> {
                            binding.interceptProgress.gone()
                            binding.interceptStatus.text = "Launching app..."
                            onBackPressed()
                            startActivity(Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_MANGA)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MangaController.MANGA_EXTRA, it.mangaId))
                        }
                        is InterceptResult.Failure -> {
                            binding.interceptProgress.gone()
                            binding.interceptStatus.text = "Error: ${it.reason}"
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
    }

    override fun onStop() {
        super.onStop()
        statusSubscription?.unsubscribe()
    }
}
