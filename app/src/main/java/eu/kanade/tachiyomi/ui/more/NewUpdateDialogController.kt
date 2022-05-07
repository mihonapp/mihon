package eu.kanade.tachiyomi.ui.more

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.AppUpdateService
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import io.noties.markwon.Markwon

class NewUpdateDialogController(bundle: Bundle? = null) : DialogController(bundle) {

    constructor(update: AppUpdateResult.NewUpdate) : this(
        bundleOf(
            BODY_KEY to update.release.info,
            VERSION_KEY to update.release.version,
            RELEASE_URL_KEY to update.release.releaseLink,
            DOWNLOAD_URL_KEY to update.release.getDownloadLink(),
        ),
    )

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val releaseBody = args.getString(BODY_KEY)!!
            .replace("""---(\R|.)*Checksums(\R|.)*""".toRegex(), "")
        val info = Markwon.create(activity!!).toMarkdown(releaseBody)

        return MaterialAlertDialogBuilder(activity!!)
            .setTitle(R.string.update_check_notification_update_available)
            .setMessage(info)
            .setPositiveButton(R.string.update_check_confirm) { _, _ ->
                applicationContext?.let { context ->
                    // Start download
                    val url = args.getString(DOWNLOAD_URL_KEY)!!
                    val version = args.getString(VERSION_KEY)
                    AppUpdateService.start(context, url, version)
                }
            }
            .setNeutralButton(R.string.update_check_open) { _, _ ->
                openInBrowser(args.getString(RELEASE_URL_KEY)!!)
            }
            .create()
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        // Make links in Markdown text clickable
        (dialog?.findViewById(android.R.id.message) as? TextView)?.movementMethod =
            LinkMovementMethod.getInstance()
    }
}

private const val BODY_KEY = "NewUpdateDialogController.body"
private const val VERSION_KEY = "NewUpdateDialogController.version"
private const val RELEASE_URL_KEY = "NewUpdateDialogController.release_url"
private const val DOWNLOAD_URL_KEY = "NewUpdateDialogController.download_url"
