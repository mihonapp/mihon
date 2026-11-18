package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AfragaNeon
import eu.kanade.presentation.components.NeonPill
import eu.kanade.presentation.components.SplitColorHeader
import eu.kanade.presentation.components.glassSurface
import eu.kanade.tachiyomi.ui.library.LibraryItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun LibrarySpotlightCard(
    libraryItem: LibraryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContinueReading: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val manga = libraryItem.libraryManga.manga
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .height(300.dp)
            .clip(shape)
            .background(Color(0xFF16223B))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = MangaCover(
                mangaId = manga.id,
                sourceId = manga.source,
                isMangaFavorite = manga.favorite,
                url = manga.thumbnailUrl,
                lastModified = manga.coverLastModified,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(170.dp)
                .blur(16.dp)
                .alpha(0.46f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF0081020),
                        0.48f to Color(0xC6081020),
                        1f to Color(0x1A081020),
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x18081020),
                        1f to Color(0x9E081020),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .glassSurface(
                    shape = RoundedCornerShape(24.dp),
                    tint = Color(0x99191E30),
                    border = Color.White.copy(alpha = 0.16f),
                    glow = Color(0xFF8C6CFF),
                    cornerRadius = 24.dp,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            val eyebrow = stringResource(MR.strings.library_spotlight_eyebrow)
            SplitColorHeader(
                firstWord = eyebrow.substringBefore(' '),
                secondWord = eyebrow.substringAfter(' ', eyebrow),
            )
            Text(
                text = manga.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NeonPill(
                    text = libraryItem.sourceName.take(18),
                    tone = AfragaNeon.Cyan,
                )
                Button(
                    onClick = onContinueReading ?: onClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8C6CFF),
                        contentColor = Color.White,
                    ),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            if (onContinueReading != null) MR.strings.action_continue_reading else MR.strings.action_open_manga,
                        ),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
