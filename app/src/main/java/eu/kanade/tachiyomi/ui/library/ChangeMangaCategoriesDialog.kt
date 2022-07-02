package eu.kanade.tachiyomi.ui.library

import android.app.Dialog
import android.os.Bundle
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems

class ChangeMangaCategoriesDialog<T>(bundle: Bundle? = null) :
    DialogController(bundle) where T : Controller, T : ChangeMangaCategoriesDialog.Listener {

    private var mangas = emptyList<Manga>()
    private var categories = emptyList<Category>()
    private var preselected = emptyArray<Int>()
    private var selected = emptyArray<Int>().toIntArray()

    constructor(
        target: T,
        mangas: List<Manga>,
        categories: List<Category>,
        preselected: Array<Int>,
    ) : this() {
        this.mangas = mangas
        this.categories = categories
        this.preselected = preselected
        this.selected = preselected.toIntArray()
        targetController = target
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.action_move_category)
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                if (categories.isNotEmpty()) {
                    setQuadStateMultiChoiceItems(
                        items = categories.map { it.name },
                        isActionList = false,
                        initialSelected = preselected.toIntArray(),
                    ) { selections ->
                        selected = selections
                    }
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        val add = selected
                            .mapIndexed { index, value -> if (value == QuadStateTextView.State.CHECKED.ordinal) categories[index] else null }
                            .filterNotNull()
                        val remove = selected
                            .mapIndexed { index, value -> if (value == QuadStateTextView.State.UNCHECKED.ordinal) categories[index] else null }
                            .filterNotNull()
                        (targetController as? Listener)?.updateCategoriesForMangas(mangas, add, remove)
                    }
                    setNeutralButton(R.string.action_edit) { _, _ -> openCategoryController() }
                } else {
                    setMessage(R.string.information_empty_category_dialog)
                    setPositiveButton(R.string.action_edit_categories) { _, _ -> openCategoryController() }
                }
            }
            .create()
    }

    private fun openCategoryController() {
        if (targetController is LibraryController) {
            val libController = targetController as LibraryController
            libController.clearSelection()
        }
        router.popCurrentController()
        router.pushController(CategoryController())
    }

    interface Listener {
        fun updateCategoriesForMangas(mangas: List<Manga>, addCategories: List<Category>, removeCategories: List<Category> = emptyList<Category>())
    }
}
