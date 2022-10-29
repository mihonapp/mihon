package eu.kanade.tachiyomi.ui.manga.chapter

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.chapter.interactor.SetMangaDefaultChapterFlags
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.DialogCheckboxView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

class SetChapterSettingsDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags by injectLazy()

    constructor(manga: Manga) : this(
        bundleOf(MANGA_KEY to manga),
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val view = DialogCheckboxView(activity!!).apply {
            setDescription(R.string.confirm_set_chapter_settings)
            setOptionDescription(R.string.also_set_chapter_settings_for_library)
        }

        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.chapter_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                libraryPreferences.setChapterSettingsDefault(args.getSerializableCompat(MANGA_KEY)!!)
                if (view.isChecked()) {
                    scope.launch {
                        setMangaDefaultChapterFlags.awaitAll()
                    }
                }

                activity?.toast(R.string.chapter_settings_updated)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private const val MANGA_KEY = "manga"
