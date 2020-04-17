package eu.kanade.tachiyomi.ui.migration

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationListController

class MigrationMangaDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
    where T : Controller {

    var copy = false
    var mangaSet = 0
    var mangaSkipped = 0
    constructor(target: T, copy: Boolean, mangaSet: Int, mangaSkipped: Int) : this() {
        targetController = target
        this.copy = copy
        this.mangaSet = mangaSet
        this.mangaSkipped = mangaSkipped
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val confirmRes = if (copy) R.string.confirm_copy else R.string.confirm_migration
        val confirmString = applicationContext?.getString(confirmRes, mangaSet, (
            if (mangaSkipped > 0)
                " " + applicationContext?.getString(R.string.skipping_x, mangaSkipped) ?: ""
            else "")) ?: ""
        return MaterialDialog.Builder(activity!!)
            .content(confirmString)
            .positiveText(android.R.string.yes)
            .negativeText(android.R.string.no)
            .onPositive { _, _ ->
                if (copy)
                    (targetController as? MigrationListController)?.copyMangas()
                else
                    (targetController as? MigrationListController)?.migrateMangas()
            }.show()
    }
}
