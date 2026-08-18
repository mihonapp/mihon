package eu.kanade.presentation.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.components.material.padding
import kotlin.math.max

@Composable
fun PanelPageGridSheet(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(MaterialTheme.padding.small),
        ) {
            items(pages, key = { it.index }) { page ->
                PageGridCell(
                    page = page,
                    isSelected = page.index == currentPageIndex,
                    onClick = {
                        onSelectPage(page.index)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun PageGridCell(page: ReaderPage, isSelected: Boolean, onClick: () -> Unit) {
    val cacheKey = remember(page) { "${page.chapter.chapter.id}_${page.index}" }
    val thumbnail by produceState<Bitmap?>(initialValue = PanelPageThumbnailCache.get(cacheKey), key1 = cacheKey) {
        value = PanelPageThumbnailCache.get(cacheKey) ?: decodePageThumbnail(page)?.also {
            PanelPageThumbnailCache.put(cacheKey, it)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.7f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
        Text(
            text = "${page.index + 1}",
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private suspend fun decodePageThumbnail(page: ReaderPage): Bitmap? = withContext(Dispatchers.IO) {
    var streamFn = page.stream
    if (streamFn == null) {
        page.chapter.pageLoader?.loadPage(page)
        page.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
        streamFn = page.stream ?: return@withContext null
    }
    try {
        streamFn.invoke().use { input ->
            val bytes = input.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > THUMBNAIL_MAX_DIMENSION) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Throwable) {
        null
    }
}

// Long side, in px. Grid cells are sized from a 96dp adaptive minimum with a 0.7 aspect ratio,
// which on a high-density phone display is comfortably larger than the old 200px target — that
// mismatch was what made every thumbnail look blurry (the decoded bitmap was being upscaled to
// fill a cell well past its own resolution).
private const val THUMBNAIL_MAX_DIMENSION = 480

private object PanelPageThumbnailCache {
    private const val MAX_ENTRIES = 40
    private val cache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(key: String): Bitmap? = cache[key]

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        cache[key] = bitmap
    }
}
