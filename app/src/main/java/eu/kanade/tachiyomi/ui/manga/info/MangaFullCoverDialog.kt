package eu.kanade.tachiyomi.ui.manga.info

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.MangaFullCoverDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.util.view.setNavigationBarTransparentCompat
import eu.kanade.tachiyomi.widget.TachiyomiFullscreenDialog
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaFullCoverDialog : DialogController {

    private var manga: Manga? = null

    private var binding: MangaFullCoverDialogBinding? = null

    private var disposable: Disposable? = null

    private val mangaController
        get() = targetController as MangaController?

    constructor(targetController: MangaController, manga: Manga) : super(bundleOf("mangaId" to manga.id)) {
        this.targetController = targetController
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        val db = Injekt.get<DatabaseHelper>()
        manga = db.getManga(bundle.getLong("mangaId")).executeAsBlocking()
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = MangaFullCoverDialogBinding.inflate(activity!!.layoutInflater)

        binding?.toolbar?.apply {
            setNavigationOnClickListener { dialog?.dismiss() }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_share_cover -> mangaController?.shareCover()
                    R.id.action_save_cover -> mangaController?.saveCover()
                    R.id.action_edit_cover -> mangaController?.changeCover()
                }
                true
            }
            menu?.findItem(R.id.action_edit_cover)?.isVisible = manga?.favorite ?: false
        }

        setImage(manga)

        binding?.appbar?.applyInsetter {
            type(navigationBars = true, statusBars = true) {
                padding(left = true, top = true, right = true)
            }
        }

        binding?.container?.onViewClicked = { dialog?.dismiss() }
        binding?.container?.applyInsetter {
            type(navigationBars = true) {
                padding(bottom = true)
            }
        }

        return TachiyomiFullscreenDialog(activity!!, binding!!.root).apply {
            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            window?.setBackgroundDrawable(ColorDrawable(ColorUtils.setAlphaComponent(typedValue.data, 230)))
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        dialog?.window?.let { window ->
            window.setNavigationBarTransparentCompat(window.context)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        disposable?.dispose()
        disposable = null
    }

    fun setImage(manga: Manga?) {
        if (manga == null) return
        val request = ImageRequest.Builder(applicationContext!!)
            .data(manga)
            .target {
                binding?.container?.setImage(
                    it,
                    ReaderPageImageView.Config(
                        zoomDuration = 500
                    )
                )
            }
            .build()

        disposable = applicationContext?.imageLoader?.enqueue(request)
    }
}
