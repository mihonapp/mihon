package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.modifierElementOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.util.selectedBackground

object CommonMangaItemDefaults {
    val GridHorizontalSpacer = 4.dp
    val GridVerticalSpacer = 4.dp

    const val BrowseFavoriteCoverAlpha = 0.34f
}

private const val GridSelectedCoverAlpha = 0.76f

/**
 * Layout of grid list item with title overlaying the cover.
 * Accepts null [title] for a cover-only view.
 */
@Composable
fun MangaCompactGridItem(
    isSelected: Boolean = false,
    title: String? = null,
    coverData: eu.kanade.domain.manga.model.MangaCover,
    coverAlpha: Float = 1f,
    coverBadgeStart: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        MangaGridCover(
            cover = {
                MangaCover.Book(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha),
                    data = coverData,
                )
            },
            badgesStart = coverBadgeStart,
            badgesEnd = coverBadgeEnd,
            content = {
                if (title != null) {
                    CoverTextOverlay(title = title)
                }
            },
        )
    }
}

/**
 * Title overlay for [MangaCompactGridItem]
 */
@Composable
private fun BoxScope.CoverTextOverlay(title: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    GridItemTitle(
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.BottomStart),
        title = title,
        style = MaterialTheme.typography.titleSmall.copy(
            color = Color.White,
            shadow = Shadow(
                color = Color.Black,
                blurRadius = 4f,
            ),
        ),
    )
}

/**
 * Layout of grid list item with title below the cover.
 */
@Composable
fun MangaComfortableGridItem(
    isSelected: Boolean = false,
    title: String,
    coverData: eu.kanade.domain.manga.model.MangaCover,
    coverAlpha: Float = 1f,
    coverBadgeStart: (@Composable RowScope.() -> Unit)? = null,
    coverBadgeEnd: (@Composable RowScope.() -> Unit)? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    GridItemSelectable(
        isSelected = isSelected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Column {
            MangaGridCover(
                cover = {
                    MangaCover.Book(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isSelected) GridSelectedCoverAlpha else coverAlpha),
                        data = coverData,
                    )
                },
                badgesStart = coverBadgeStart,
                badgesEnd = coverBadgeEnd,
            )
            GridItemTitle(
                modifier = Modifier.padding(4.dp),
                title = title,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/**
 * Common cover layout to add contents to be drawn on top of the cover.
 */
@Composable
private fun MangaGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    badgesStart: (@Composable RowScope.() -> Unit)? = null,
    badgesEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MangaCover.Book.ratio),
    ) {
        cover()
        content?.invoke(this)
        if (badgesStart != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
                content = badgesStart,
            )
        }

        if (badgesEnd != null) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
                content = badgesEnd,
            )
        }
    }
}

@Composable
private fun GridItemTitle(
    modifier: Modifier,
    title: String,
    style: TextStyle,
) {
    Text(
        modifier = modifier,
        text = title,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/**
 * Wrapper for grid items to handle selection state, click and long click.
 */
@Composable
private fun GridItemSelectable(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .selectedOutline(isSelected = isSelected, color = MaterialTheme.colorScheme.secondary)
            .padding(4.dp),
    ) {
        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            LocalContentColor.current
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * @see GridItemSelectable
 */
private fun Modifier.selectedOutline(
    isSelected: Boolean,
    color: Color,
): Modifier {
    class SelectedOutlineNode(var selected: Boolean, var color: Color) : DrawModifierNode, Modifier.Node() {
        override fun ContentDrawScope.draw() {
            if (selected) drawRect(color)
            drawContent()
        }
    }

    return this then modifierElementOf(
        params = isSelected.hashCode() + color.hashCode(),
        create = { SelectedOutlineNode(isSelected, color) },
        update = {
            it.selected = isSelected
            it.color = color
        },
        definitions = {
            name = "selectionOutline"
            properties["isSelected"] = isSelected
            properties["color"] = color
        },
    )
}

/**
 * Layout of list item.
 */
@Composable
fun MangaListItem(
    isSelected: Boolean = false,
    title: String,
    coverData: eu.kanade.domain.manga.model.MangaCover,
    coverAlpha: Float = 1f,
    badge: @Composable RowScope.() -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .height(56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .fillMaxHeight()
                .alpha(coverAlpha),
            data = coverData,
        )
        Text(
            text = title,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        BadgeGroup(content = badge)
    }
}
