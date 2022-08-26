package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.os.Bundle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Controller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import uy.kohesive.injekt.injectLazy

class AddDuplicateMangaDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val sourceManager: SourceManager by injectLazy()

    private lateinit var libraryManga: Manga
    private lateinit var onAddToLibrary: () -> Unit

    constructor(
        target: Controller,
        libraryManga: Manga,
        onAddToLibrary: () -> Unit,
    ) : this() {
        targetController = target

        this.libraryManga = libraryManga
        this.onAddToLibrary = onAddToLibrary
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val source = sourceManager.getOrStub(libraryManga.source)

        return MaterialAlertDialogBuilder(activity!!)
            .setMessage(activity?.getString(R.string.confirm_manga_add_duplicate, source.name))
            .setPositiveButton(activity?.getString(R.string.action_add)) { _, _ ->
                onAddToLibrary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(activity?.getString(R.string.action_show_manga)) { _, _ ->
                dismissDialog()
                router.pushController(MangaController(libraryManga.id))
            }
            .setCancelable(true)
            .create()
    }
}

@Composable
fun DuplicateDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: () -> Unit,
    duplicateFrom: Source,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onDismissRequest()
                    onOpenManga()
                },) {
                    Text(text = stringResource(id = R.string.action_show_manga))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(id = R.string.action_add))
                }
            }
        },
        title = {
            Text(text = stringResource(id = R.string.are_you_sure))
        },
        text = {
            Text(
                text = stringResource(
                    id = R.string.confirm_manga_add_duplicate,
                    duplicateFrom.name,
                ),
            )
        },
    )
}
