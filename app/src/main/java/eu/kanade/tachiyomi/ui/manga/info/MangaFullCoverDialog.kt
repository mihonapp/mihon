package eu.kanade.tachiyomi.ui.manga.info

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.core.os.bundleOf
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import eu.kanade.domain.manga.interactor.GetManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority
import nucleus.presenter.Presenter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangaFullCoverDialog : FullComposeController<MangaFullCoverDialog.MangaFullCoverPresenter> {

    private val mangaId: Long

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    constructor(
        mangaId: Long,
    ) : super(bundleOf(MANGA_EXTRA to mangaId)) {
        this.mangaId = mangaId
    }

    override fun createPresenter() = MangaFullCoverPresenter(mangaId)

    @Composable
    override fun ComposeContent() {
        val manga = presenter.manga.collectAsState().value
        if (manga != null) {
            MangaCoverDialog(
                coverDataProvider = { manga },
                isCustomCover = remember(manga) { manga.hasCustomCover() },
                onShareClick = this::shareCover,
                onSaveClick = this::saveCover,
                onEditClick = this::changeCover,
                onDismissRequest = router::popCurrentController,
            )
        } else {
            LoadingScreen()
        }
    }

    private fun shareCover() {
        val activity = activity ?: return
        viewScope.launchIO {
            try {
                val uri = presenter.saveCover(activity, temp = true) ?: return@launchIO
                withUIContext {
                    startActivity(uri.toShareIntent(activity))
                }
            } catch (e: Throwable) {
                withUIContext {
                    logcat(LogPriority.ERROR, e)
                    activity.toast(R.string.error_sharing_cover)
                }
            }
        }
    }

    private fun saveCover() {
        val activity = activity ?: return
        viewScope.launchIO {
            try {
                presenter.saveCover(activity, temp = false)
                withUIContext {
                    activity.toast(R.string.cover_saved)
                }
            } catch (e: Throwable) {
                withUIContext {
                    logcat(LogPriority.ERROR, e)
                    activity.toast(R.string.error_saving_cover)
                }
            }
        }
    }

    private fun changeCover(action: EditCoverAction) {
        when (action) {
            EditCoverAction.EDIT -> {
                // This will open new Photo Picker eventually.
                // See https://github.com/tachiyomiorg/tachiyomi/pull/8253#issuecomment-1285747310
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                startActivityForResult(
                    Intent.createChooser(intent, resources?.getString(R.string.file_select_cover)),
                    REQUEST_IMAGE_OPEN,
                )
            }
            EditCoverAction.DELETE -> presenter.deleteCustomCover()
        }
    }

    private fun onSetCoverSuccess() {
        activity?.toast(R.string.cover_updated)
    }

    private fun onSetCoverError(error: Throwable) {
        activity?.toast(R.string.notification_cover_update_failed)
        logcat(LogPriority.ERROR, error)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_OPEN) {
            val dataUri = data?.data
            if (dataUri == null || resultCode != Activity.RESULT_OK) return
            val activity = activity ?: return
            presenter.editCover(activity, dataUri)
        }
    }

    inner class MangaFullCoverPresenter(
        private val mangaId: Long,
        private val getManga: GetManga = Injekt.get(),
    ) : Presenter<MangaFullCoverDialog>() {

        private var presenterScope: CoroutineScope = MainScope()

        private val _mangaFlow = MutableStateFlow<Manga?>(null)
        val manga = _mangaFlow.asStateFlow()

        private val imageSaver by injectLazy<ImageSaver>()
        private val coverCache by injectLazy<CoverCache>()
        private val updateManga by injectLazy<UpdateManga>()

        override fun onCreate(savedState: Bundle?) {
            super.onCreate(savedState)
            presenterScope.launchIO {
                getManga.subscribe(mangaId)
                    .collect { _mangaFlow.value = it }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            presenterScope.cancel()
        }

        /**
         * Save manga cover Bitmap to picture or temporary share directory.
         *
         * @param context The context for building and executing the ImageRequest
         * @return the uri to saved file
         */
        suspend fun saveCover(context: Context, temp: Boolean): Uri? {
            val manga = manga.value ?: return null
            val req = ImageRequest.Builder(context)
                .data(manga)
                .size(Size.ORIGINAL)
                .build()
            val result = context.imageLoader.execute(req).drawable

            // TODO: Handle animated cover
            val bitmap = (result as? BitmapDrawable)?.bitmap ?: return null
            return imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }

        /**
         * Update cover with local file.
         *
         * @param context Context.
         * @param data uri of the cover resource.
         */
        fun editCover(context: Context, data: Uri) {
            val manga = manga.value ?: return
            presenterScope.launchIO {
                @Suppress("BlockingMethodInNonBlockingContext")
                context.contentResolver.openInputStream(data)?.use {
                    try {
                        manga.editCover(context, it, updateManga, coverCache)
                        withUIContext { view?.onSetCoverSuccess() }
                    } catch (e: Exception) {
                        withUIContext { view?.onSetCoverError(e) }
                    }
                }
            }
        }

        fun deleteCustomCover() {
            val mangaId = manga.value?.id ?: return
            presenterScope.launchIO {
                try {
                    coverCache.deleteCustomCover(mangaId)
                    updateManga.awaitUpdateCoverLastModified(mangaId)
                    withUIContext { view?.onSetCoverSuccess() }
                } catch (e: Exception) {
                    withUIContext { view?.onSetCoverError(e) }
                }
            }
        }
    }

    companion object {
        private const val MANGA_EXTRA = "mangaId"

        /**
         * Key to change the cover of a manga in [onActivityResult].
         */
        private const val REQUEST_IMAGE_OPEN = 101
    }
}
