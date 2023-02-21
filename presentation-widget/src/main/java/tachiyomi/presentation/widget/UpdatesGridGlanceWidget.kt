package tachiyomi.presentation.widget

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import coil.executeBlocking
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.coroutines.MainScope
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.presentation.widget.components.CoverHeight
import tachiyomi.presentation.widget.components.CoverWidth
import tachiyomi.presentation.widget.components.LockedWidget
import tachiyomi.presentation.widget.components.UpdatesWidget
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.Date

class UpdatesGridGlanceWidget : GlanceAppWidget() {

    private val app: Application by injectLazy()
    private val preferences: SecurityPreferences by injectLazy()

    private val coroutineScope = MainScope()

    private var data: List<Pair<Long, Bitmap?>>? = null

    override val sizeMode = SizeMode.Exact

    @Composable
    override fun Content() {
        // If app lock enabled, don't do anything
        if (preferences.useAuthenticator().get()) {
            LockedWidget()
            return
        }
        UpdatesWidget(data)
    }

    fun loadData(list: List<UpdatesWithRelations>? = null) {
        coroutineScope.launchIO {
            // Don't show anything when lock is active
            if (preferences.useAuthenticator().get()) {
                updateAll(app)
                return@launchIO
            }

            val manager = GlanceAppWidgetManager(app)
            val ids = manager.getGlanceIds(this@UpdatesGridGlanceWidget::class.java)
            if (ids.isEmpty()) return@launchIO

            val processList = list
                ?: Injekt.get<GetUpdates>().await(
                    read = false,
                    after = DateLimit.timeInMillis,
                )
            val (rowCount, columnCount) = ids
                .flatMap { manager.getAppWidgetSizes(it) }
                .maxBy { it.height.value * it.width.value }
                .calculateRowAndColumnCount()

            data = prepareList(processList, rowCount * columnCount)
            ids.forEach { update(app, it) }
        }
    }

    private fun prepareList(processList: List<UpdatesWithRelations>, take: Int): List<Pair<Long, Bitmap?>> {
        // Resize to cover size
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val roundPx = app.resources.getDimension(R.dimen.appwidget_inner_radius)
        return processList
            .distinctBy { it.mangaId }
            .take(take)
            .map { updatesView ->
                val request = ImageRequest.Builder(app)
                    .data(
                        MangaCover(
                            mangaId = updatesView.mangaId,
                            sourceId = updatesView.sourceId,
                            isMangaFavorite = true,
                            url = updatesView.coverData.url,
                            lastModified = updatesView.coverData.lastModified,
                        ),
                    )
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .precision(Precision.EXACT)
                    .size(widthPx, heightPx)
                    .scale(Scale.FILL)
                    .let {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            it.transformations(RoundedCornersTransformation(roundPx))
                        } else {
                            it // Handled by system
                        }
                    }
                    .build()
                Pair(updatesView.mangaId, app.imageLoader.executeBlocking(request).drawable?.toBitmap())
            }
    }

    companion object {
        val DateLimit: Calendar
            get() = Calendar.getInstance().apply {
                time = Date()
                add(Calendar.MONTH, -3)
            }
    }
}

val ContainerModifier = GlanceModifier
    .fillMaxSize()
    .background(ImageProvider(R.drawable.appwidget_background))
    .appWidgetBackground()
    .appWidgetBackgroundRadius()
