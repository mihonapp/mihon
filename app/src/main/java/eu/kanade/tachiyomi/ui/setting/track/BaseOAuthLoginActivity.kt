package eu.kanade.tachiyomi.ui.setting.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import uy.kohesive.injekt.injectLazy

abstract class BaseOAuthLoginActivity : BaseActivity() {

    internal val trackManager: TrackManager by injectLazy()

    abstract fun handleResult(data: Uri?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setComposeContent {
            LoadingScreen()
        }

        handleResult(intent.data)
    }

    internal fun returnToSettings() {
        finish()

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
}
