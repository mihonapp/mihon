package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import uy.kohesive.injekt.injectLazy

class AddDuplicateMangaDialog<T>(bundle: Bundle? = null) : DialogController(bundle)
    where T : Controller, T : AddDuplicateMangaDialog.Listener {

    private val sourceManager: SourceManager by injectLazy()

    private lateinit var libraryManga: Manga
    private lateinit var newManga: Manga

    constructor(target: T, libraryManga: Manga, newManga: Manga) : this() {
        targetController = target

        this.libraryManga = libraryManga
        this.newManga = newManga
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val source = sourceManager.getOrStub(libraryManga.source)

        return MaterialAlertDialogBuilder(activity!!)
            .setMessage(activity?.getString(R.string.confirm_manga_add_duplicate, source.name))
            .setPositiveButton(activity?.getString(R.string.action_add)) { _, _ ->
                (targetController as? Listener)?.addToLibrary(newManga)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(activity?.getString(R.string.action_show_manga)) { _, _ ->
                dismissDialog()
                router.pushController(MangaController(libraryManga))
            }
            .setCancelable(true)
            .create()
    }

    interface Listener {
        fun addToLibrary(newManga: Manga)
    }
}
