package exh.ui.batchadd

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.fragment.BaseFragment
import exh.GalleryAdder
import exh.metadata.nullIfBlank
import kotlinx.android.synthetic.main.eh_fragment_batch_add.*
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * LoginActivity
 */

class BatchAddFragment : BaseFragment() {

    private val galleryAdder by lazy { GalleryAdder() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?)
        = inflater.inflate(R.layout.eh_fragment_batch_add, container, false)!!

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        setToolbarTitle("Batch Add")

        setup()
    }

    fun setup() {
        btn_add_galleries.setOnClickListener {
            val galleries = galleries_box.text.toString()
            //Check text box has content
            if(galleries.isNullOrBlank())
                noGalleriesSpecified()

            //Too lazy to actually deal with orientation changes
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR

            val splitGalleries = galleries.split("\n").map {
                it.trim().nullIfBlank()
            }.filterNotNull()

            val dialog = MaterialDialog.Builder(context)
                    .title("Adding galleries...")
                    .progress(false, splitGalleries.size, true)
                    .cancelable(false)
                    .canceledOnTouchOutside(false)
                    .show()

            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<String>()

            thread {
                splitGalleries.forEachIndexed { i, s ->
                    activity.runOnUiThread {
                        dialog.setContent("Processing: $s")
                    }
                    if(addGallery(s)) {
                        succeeded.add(s)
                    } else {
                        failed.add(s)
                    }
                    activity.runOnUiThread {
                        dialog.setProgress(i + 1)
                    }
                }

                //Show report
                if(succeeded.isEmpty()) succeeded += "None"
                if(failed.isEmpty()) failed += "None"
                val succeededReport = succeeded.joinToString(separator = "\n", prefix = "Added:\n")
                val failedReport = failed.joinToString(separator = "\n", prefix = "Failed:\n")

                val summary = "Summary:\nAdded: ${succeeded.size} gallerie(s)\nFailed: ${failed.size} gallerie(s)"

                val report = listOf(succeededReport, failedReport, summary).joinToString(separator = "\n\n")

                activity.runOnUiThread {
                    //Enable orientation changes again
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

                    dialog.dismiss()

                    MaterialDialog.Builder(context)
                            .title("Batch add report")
                            .content(report)
                            .positiveText("Ok")
                            .cancelable(true)
                            .canceledOnTouchOutside(true)
                            .show()
                }
            }

        }
    }

    fun addGallery(url: String): Boolean {
        try {
            galleryAdder.addGallery(url, true)
        } catch(t: Throwable) {
            Timber.e(t, "Could not add gallery!")
            return false
        }
        return true
    }

    fun noGalleriesSpecified() {
        MaterialDialog.Builder(context)
                .title("No galleries to add!")
                .content("You must specify at least one gallery to add!")
                .positiveText("Ok")
                .onPositive { materialDialog, dialogAction -> materialDialog.dismiss() }
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .show()
    }

    companion object {
        fun newInstance() = BatchAddFragment()
    }
}
