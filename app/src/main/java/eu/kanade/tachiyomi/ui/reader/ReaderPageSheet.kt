package eu.kanade.tachiyomi.ui.reader

import com.afollestad.materialdialogs.MaterialDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ReaderPageSheetBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.widget.sheet.BaseBottomSheetDialog

/**
 * Sheet to show when a page is long clicked.
 */
class ReaderPageSheet(
    private val activity: ReaderActivity,
    private val page: ReaderPage
) : BaseBottomSheetDialog(activity) {

    private val binding = ReaderPageSheetBinding.inflate(activity.layoutInflater, null, false)

    init {
        setContentView(binding.root)

        binding.setAsCoverLayout.setOnClickListener { setAsCover() }
        binding.shareLayout.setOnClickListener { share() }
        binding.saveLayout.setOnClickListener { save() }
    }

    /**
     * Sets the image of this page as the cover of the manga.
     */
    private fun setAsCover() {
        if (page.status != Page.READY) return

        MaterialDialog(activity)
            .message(R.string.confirm_set_image_as_cover)
            .positiveButton(android.R.string.ok) {
                activity.setAsCover(page)
                dismiss()
            }
            .negativeButton(android.R.string.cancel)
            .show()
    }

    /**
     * Shares the image of this page with external apps.
     */
    private fun share() {
        activity.shareImage(page)
        dismiss()
    }

    /**
     * Saves the image of this page on external storage.
     */
    private fun save() {
        activity.saveImage(page)
        dismiss()
    }
}
