package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCoverViewModel(
    private val mangaId: Long,
    private val getManga: GetManga = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateViewModel<Manga?>(null) {

    companion object {
        val MANGA_ID_KEY = CreationExtras.Key<Long>()

        val Factory = viewModelFactory {
            initializer {
                MangaCoverViewModel(
                    mangaId = get(MANGA_ID_KEY)!!,
                )
            }
        }
    }

    init {
        viewModelScope.launchIO {
            getManga.subscribe(mangaId)
                .collect { newManga -> mutableState.update { newManga } }
        }
    }

    fun saveCover(context: Context) {
        viewModelScope.launch {
            try {
                saveCoverInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        viewModelScope.launch {
            try {
                val uri = saveCoverInternal(context, temp = true) ?: return@launch
                withUIContext {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save manga cover Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveCoverInternal(context: Context, temp: Boolean): Uri? {
        val manga = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(manga)
            .size(Size.ORIGINAL)
            .build()

        return withIOContext {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated cover
            val bitmap = result?.getBitmapOrNull() ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = manga.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    /**
     * Update cover with local file.
     *
     * @param context Context.
     * @param data uri of the cover resource.
     */
    fun editCover(context: Context, data: Uri) {
        val manga = state.value ?: return
        viewModelScope.launchIO {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    manga.editCover(Injekt.get(), it, updateManga, coverCache)
                    notifyCoverUpdated(context)
                } catch (e: Exception) {
                    notifyFailedCoverUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomCover(context: Context) {
        val mangaId = state.value?.id ?: return
        viewModelScope.launchIO {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.cover_updated),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_cover_update_failed),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
