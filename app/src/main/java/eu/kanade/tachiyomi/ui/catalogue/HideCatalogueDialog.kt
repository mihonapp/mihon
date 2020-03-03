package eu.kanade.tachiyomi.ui.catalogue

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HideCatalogueDialog(bundle: Bundle? = null) : DialogController(bundle) {

    private val source = Injekt.get<SourceManager>().get(args.getLong("key"))!!

    constructor(source: Source) : this(Bundle().apply { putLong("key", source.id) })

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        return MaterialDialog.Builder(activity!!)
                .title(activity!!.getString(R.string.hide_catalogue, source.name))
                .positiveText(android.R.string.ok)
                .onPositive { _, _ ->
                    (targetController as? Listener)?.hideCatalogueDialogClosed(source)
                }
                .negativeText(android.R.string.cancel)
                .build()
    }

    interface Listener {
        fun hideCatalogueDialogClosed(source: Source)
    }
}
