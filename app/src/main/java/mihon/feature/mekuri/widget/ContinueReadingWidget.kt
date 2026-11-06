package mihon.feature.mekuri.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import mihon.feature.mekuri.vault.MekuriVault
import tachiyomi.core.common.Constants
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.model.MangaCover

/**
 * Mekuri: home-screen widget showing the manga read most recently, with its cover. Tapping it opens the manga.
 * Vault manga and sources are never shown, and it shows nothing when the app lock is on.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.appGraph
        val locked = graph.securityPreferences.useAuthenticator.get()

        provideContent {
            val flow = remember {
                graph.getHistory.subscribe("").map { history ->
                    history.pickVisible(context)?.let {
                        it to
                            cover(context, it)
                    }
                }
            }
            val latest by flow.collectAsState(initial = null)
            Content(context, locked, latest)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, locked: Boolean, latest: Pair<HistoryWithRelations, Bitmap?>?) {
        val ink = ColorProvider(Color(0xFFE8E5F2))
        val muted = ColorProvider(Color(0xFFA39DB8))
        val coral = ColorProvider(Color(0xFFFF8A6B))
        val open = Intent(context, Class.forName(Constants.MAIN_ACTIVITY)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val item = latest?.first
            if (!locked && item != null) {
                action = Constants.SHORTCUT_MANGA
                putExtra(Constants.MANGA_EXTRA, item.mangaId)
            }
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(24.dp)
                .background(Color(0xFF15131D))
                .clickable(actionStartActivity(open))
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val item = latest?.first
            when {
                locked -> Text("Mekuri is locked", style = TextStyle(color = muted, fontSize = 14.sp))
                item == null -> Text("Nothing read yet", style = TextStyle(color = muted, fontSize = 14.sp))
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val bitmap = latest.second
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.size(width = 56.dp, height = 84.dp).cornerRadius(10.dp),
                        )
                        Spacer(GlanceModifier.width(14.dp))
                    }
                    Column {
                        Text(
                            "CONTINUE READING",
                            style = TextStyle(color = coral, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = item.title,
                            maxLines = 2,
                            style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        if (item.chapterNumber > 0) {
                            val number = item.chapterNumber.let {
                                if (it % 1.0 ==
                                    0.0
                                ) {
                                    it.toInt().toString()
                                } else {
                                    it.toString()
                                }
                            }
                            Text("Chapter $number", style = TextStyle(color = muted, fontSize = 13.sp))
                        }
                    }
                }
            }
        }
    }

    private suspend fun List<HistoryWithRelations>.pickVisible(context: Context): HistoryWithRelations? {
        val graph = context.appGraph
        val vaultCategories = MekuriVault.storedCategories()
        val vaultSources = MekuriVault.storedSources()
        return firstOrNull { item ->
            if (item.coverData.sourceId in vaultSources) return@firstOrNull false
            if (vaultCategories.isEmpty()) return@firstOrNull true
            val categories = graph.getCategories.await(item.mangaId).map { it.id }
            !(categories.isNotEmpty() && categories.all { it in vaultCategories })
        }
    }

    private suspend fun cover(context: Context, item: HistoryWithRelations): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(
                MangaCover(
                    mangaId = item.mangaId,
                    sourceId = item.coverData.sourceId,
                    isMangaFavorite = item.coverData.isMangaFavorite,
                    url = item.coverData.url,
                    lastModified = item.coverData.lastModified,
                ),
            )
            .allowHardware(false)
            .size(168, 252)
            .build()
        (context.imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(context.resources)?.toBitmap()
    }
}

class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
