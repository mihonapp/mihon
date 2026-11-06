package mihon.feature.mekuri.shelf

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import kotlin.math.abs

/** Mekuri: one spine on the bookshelf. */
@Immutable
data class Shelf(
    val key: Long,
    val name: String,
    val count: Int,
    val unread: Int,
    /** The cover the spine colour is taken from: the most recently read manga in the shelf. */
    val coverManga: Manga?,
    /** Index into the library's displayed categories; -1 for the vault spine. */
    val categoryIndex: Int = -1,
    val kind: Kind = Kind.Normal,
    /** For [Kind.Smart]: which smart shelf, and the manga on it in order. */
    val smart: SmartShelf? = null,
    val mangaIds: List<Long> = emptyList(),
    /** Chapters arrived (in a library update) since you last read something on this shelf, and are still unread. */
    val hasUpdates: Boolean = false,
    /** Up to three covers to show on the shelf's card, most recently read first. */
    val covers: List<Manga> = emptyList(),
) {
    enum class Kind {
        Normal,

        /** A shelf that lives in the vault (only shown while it's unlocked). */
        Hidden,

        /** The vault itself, always the last spine. */
        Vault,

        /** A smart shelf that fills itself (Recently read, Unread, ...); not a category. */
        Smart,
    }
}

/** Key of the vault spine; distinct from category ids, smart shelf keys and the "nothing opened yet" marker. */
const val VAULT_SPINE_KEY = -1000L

internal object SpineMetrics {
    val MinWidth: Dp = 48.dp
    val MaxWidth: Dp = 108.dp
    val Gap: Dp = 6.dp

    /** Thicker spines for shelves with more titles, never thinner than a comfortable touch target. */
    fun width(count: Int): Dp = (26f + count * 1.7f).dp.coerceIn(MinWidth, MaxWidth)
}

/** Fallback spine colours (Mekuri palette), picked by shelf key until a cover colour is known. */
internal val ShelfPalette = listOf(
    Color(0xFF5B3FD9),
    Color(0xFFC2477A),
    Color(0xFF2F6FD6),
    Color(0xFF1F8A70),
    Color(0xFF8A4FD0),
    Color(0xFFB0532B),
    Color(0xFF3E7C8C),
    Color(0xFFB8892A),
)

internal fun fallbackColor(key: Long): Color = ShelfPalette[abs(key % ShelfPalette.size).toInt()]

/**
 * Colours for each shelf, taken from its [Shelf.coverManga] cover (average colour, kept rich and dark enough for
 * white text). Starts with the fallback colours and fills in as covers load.
 */
@Composable
internal fun rememberShelfColors(shelves: List<Shelf>): Map<Long, Color> {
    val context = LocalContext.current
    val colors = remember { mutableStateMapOf<Long, Color>() }
    LaunchedEffect(shelves.map { it.key to it.coverManga?.id }) {
        shelves.forEach { shelf ->
            val manga = shelf.coverManga ?: return@forEach
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(manga)
                    .allowHardware(false)
                    .size(48, 72)
                    .build()
                (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
            } ?: return@forEach
            colors[shelf.key] = spineColorFrom(bitmap)
        }
    }
    val pinned by ShelfPrefs.pinnedColors.collectAsState()
    return shelves.associate { it.key to (pinned[it.key]?.let(::Color) ?: colors[it.key] ?: fallbackColor(it.key)) }
}

private fun spineColorFrom(bitmap: Bitmap): Color {
    val small = bitmap.scale(12, 18, filter = true)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    for (x in 0 until small.width) {
        for (y in 0 until small.height) {
            val p = small.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
    }
    if (n == 0L) return ShelfPalette.first()
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsl)
    // Rich but not neon, and dark enough for white spine text
    hsl[1] = hsl[1].coerceIn(0.35f, 0.75f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.42f)
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = 1f).let { Color(it.toArgb()) }
}
