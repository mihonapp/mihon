package tachiyomi.presentation.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
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
import tachiyomi.core.util.lang.withIOContext
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
import java.util.Calendar
import java.util.Date

class UpdatesGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    private var data: List<Pair<Long, Bitmap?>>? = null

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        if (!locked) loadData()

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedWidget()
                return@provideContent
            }
            UpdatesWidget(data)
        }
    }

    private suspend fun loadData() {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(this@UpdatesGridGlanceWidget::class.java)
        if (ids.isEmpty()) return

        withIOContext {
            val updates = getUpdates.await(
                read = false,
                after = DateLimit.timeInMillis,
            )
            val (rowCount, columnCount) = ids
                .flatMap { manager.getAppWidgetSizes(it) }
                .maxBy { it.height.value * it.width.value }
                .calculateRowAndColumnCount()

            data = prepareList(updates, rowCount * columnCount)
        }
    }

    private fun prepareList(processList: List<UpdatesWithRelations>, take: Int): List<Pair<Long, Bitmap?>> {
        // Resize to cover size
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        return processList
            .distinctBy { it.mangaId }
            .take(take)
            .map { updatesView ->
                val request = ImageRequest.Builder(context)
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
                Pair(updatesView.mangaId, context.imageLoader.executeBlocking(request).drawable?.toBitmap())
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
