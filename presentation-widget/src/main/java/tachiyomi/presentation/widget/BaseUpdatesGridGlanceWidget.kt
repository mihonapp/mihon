package tachiyomi.presentation.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import coil.executeBlocking
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
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
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseUpdatesGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(javaClass)
        val (rowCount, columnCount) = ids
            .flatMap { manager.getAppWidgetSizes(it) }
            .maxBy { it.height.value * it.width.value }
            .calculateRowAndColumnCount(topPadding, bottomPadding)

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }

            val flow = remember {
                getUpdates
                    .subscribe(false, DateLimit.toEpochMilli())
                    .map { rawData ->
                        rawData.prepareData(rowCount, columnCount)
                    }
            }
            val data by flow.collectAsState(initial = null)
            UpdatesWidget(
                data = data,
                contentColor = foreground,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                modifier = containerModifier,
            )
        }
    }

    private suspend fun List<UpdatesWithRelations>.prepareData(
        rowCount: Int,
        columnCount: Int,
    ): ImmutableList<Pair<Long, Bitmap?>> {
        // Resize to cover size
        val widthPx = CoverWidth.value.toInt().dpToPx
        val heightPx = CoverHeight.value.toInt().dpToPx
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        return withIOContext {
            this@prepareData
                .distinctBy { it.mangaId }
                .take(rowCount * columnCount)
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
                .toImmutableList()
        }
    }

    companion object {
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
