package exh.ui.migration.manga.process

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import eu.kanade.tachiyomi.R
import exh.ui.base.BaseExhController

class MigrationProcedureController : BaseExhController() {
    override val layoutId = R.layout.eh_migration_process

    private var titleText = "Migrate manga (1/300)"

    override fun getTitle(): String {
        return titleText
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        setTitle()

        activity?.requestedOrientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}