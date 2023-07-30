package eu.kanade.presentation.manga.components

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.clickableNoIndication
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val whitespaceLineRegex = Regex("[\\r\\n]{2,}", setOf(RegexOption.MULTILINE))

@Composable
fun MangaInfoBox(
    modifier: Modifier = Modifier,
    isTabletUi: Boolean,
    appBarPadding: Dp,
    title: String,
    author: String?,
    artist: String?,
    sourceName: String,
    isStubSource: Boolean,
    coverDataProvider: () -> Manga,
    status: Long,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
) {
    Box(modifier = modifier) {
        // Backdrop
        val backdropGradientColors = listOf(
            Color.Transparent,
            MaterialTheme.colorScheme.background,
        )
        AsyncImage(
            model = coverDataProvider(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(colors = backdropGradientColors),
                    )
                }
                .blur(4.dp)
                .alpha(.2f),
        )

        // Manga & source info
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            if (!isTabletUi) {
                MangaAndSourceTitlesSmall(
                    appBarPadding = appBarPadding,
                    coverDataProvider = coverDataProvider,
                    onCoverClick = onCoverClick,
                    title = title,
                    context = LocalContext.current,
                    doSearch = doSearch,
                    author = author,
                    artist = artist,
                    status = status,
                    sourceName = sourceName,
                    isStubSource = isStubSource,
                )
            } else {
                MangaAndSourceTitlesLarge(
                    appBarPadding = appBarPadding,
                    coverDataProvider = coverDataProvider,
                    onCoverClick = onCoverClick,
                    title = title,
                    context = LocalContext.current,
                    doSearch = doSearch,
                    author = author,
                    artist = artist,
                    status = status,
                    sourceName = sourceName,
                    isStubSource = isStubSource,
                )
            }
        }
    }
}

@Composable
fun MangaActionRow(
    modifier: Modifier = Modifier,
    favorite: Boolean,
    trackingCount: Int,
    fetchInterval: Int?,
    isUserIntervalMode: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onEditIntervalClicked: (() -> Unit)?,
    onEditCategory: (() -> Unit)?,
) {
    val defaultActionButtonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)

    Row(modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)) {
        MangaActionButton(
            title = if (favorite) {
                stringResource(R.string.in_library)
            } else {
                stringResource(R.string.add_to_library)
            },
            icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (favorite) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
            onClick = onAddToLibraryClicked,
            onLongClick = onEditCategory,
        )
        if (onEditIntervalClicked != null && fetchInterval != null) {
            MangaActionButton(
                title = pluralStringResource(id = R.plurals.day, count = fetchInterval.absoluteValue, fetchInterval.absoluteValue),
                icon = Icons.Default.HourglassEmpty,
                color = if (isUserIntervalMode) MaterialTheme.colorScheme.primary else defaultActionButtonColor,
                onClick = onEditIntervalClicked,
            )
        }
        if (onTrackingClicked != null) {
            MangaActionButton(
                title = if (trackingCount == 0) {
                    stringResource(R.string.manga_tracking_tab)
                } else {
                    pluralStringResource(id = R.plurals.num_trackers, count = trackingCount, trackingCount)
                },
                icon = if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                color = if (trackingCount == 0) defaultActionButtonColor else MaterialTheme.colorScheme.primary,
                onClick = onTrackingClicked,
            )
        }
        if (onWebViewClicked != null) {
            MangaActionButton(
                title = stringResource(R.string.action_web_view),
                icon = Icons.Outlined.Public,
                color = defaultActionButtonColor,
                onClick = onWebViewClicked,
                onLongClick = onWebViewLongClicked,
            )
        }
    }
}

@Composable
fun ExpandableMangaDescription(
    modifier: Modifier = Modifier,
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,
) {
    Column(modifier = modifier) {
        val (expanded, onExpanded) = rememberSaveable {
            mutableStateOf(defaultExpandState)
        }
        val desc =
            description.takeIf { !it.isNullOrBlank() } ?: stringResource(R.string.description_placeholder)
        val trimmedDescription = remember(desc) {
            desc
                .replace(whitespaceLineRegex, "\n")
                .trimEnd()
        }
        MangaSummary(
            expandedDescription = desc,
            shrunkDescription = trimmedDescription,
            expanded = expanded,
            modifier = Modifier
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
                .clickableNoIndication { onExpanded(!expanded) },
        )
        val tags = tagsProvider()
        if (!tags.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(vertical = 12.dp)
                    .animateContentSize(),
            ) {
                var showMenu by remember { mutableStateOf(false) }
                var tagSelected by remember { mutableStateOf("") }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_search)) },
                        onClick = {
                            onTagSearch(tagSelected)
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_copy_to_clipboard)) },
                        onClick = {
                            onCopyTagToClipboard(tagSelected)
                            showMenu = false
                        },
                    )
                }
                if (expanded) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        tags.forEach {
                            TagsChip(
                                modifier = Modifier.padding(vertical = 4.dp),
                                text = it,
                                onClick = {
                                    tagSelected = it
                                    showMenu = true
                                },
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.tiny),
                    ) {
                        items(items = tags) {
                            TagsChip(
                                modifier = Modifier.padding(vertical = 4.dp),
                                text = it,
                                onClick = {
                                    tagSelected = it
                                    showMenu = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaAndSourceTitlesLarge(
    appBarPadding: Dp,
    coverDataProvider: () -> Manga,
    onCoverClick: () -> Unit,
    title: String,
    context: Context,
    doSearch: (query: String, global: Boolean) -> Unit,
    author: String?,
    artist: String?,
    status: Long,
    sourceName: String,
    isStubSource: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = appBarPadding + 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxWidth(0.65f),
            data = coverDataProvider(),
            contentDescription = stringResource(R.string.manga_cover),
            onClick = onCoverClick,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title.ifBlank { stringResource(R.string.unknown_title) },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickableNoIndication(
                onLongClick = { if (title.isNotBlank()) context.copyToClipboard(title, title) },
                onClick = { if (title.isNotBlank()) doSearch(title, true) },
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_author),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .secondaryItemAlpha()
                .padding(top = 2.dp)
                .clickableNoIndication(
                    onLongClick = {
                        if (!author.isNullOrBlank()) {
                            context.copyToClipboard(
                                author,
                                author,
                            )
                        }
                    },
                    onClick = { if (!author.isNullOrBlank()) doSearch(author, true) },
                ),
            textAlign = TextAlign.Center,
        )
        if (!artist.isNullOrBlank() && author != artist) {
            Text(
                text = artist,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp)
                    .clickableNoIndication(
                        onLongClick = { context.copyToClipboard(artist, artist) },
                        onClick = { doSearch(artist, true) },
                    ),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (status) {
                    SManga.ONGOING.toLong() -> Icons.Outlined.Schedule
                    SManga.COMPLETED.toLong() -> Icons.Outlined.DoneAll
                    SManga.LICENSED.toLong() -> Icons.Outlined.AttachMoney
                    SManga.PUBLISHING_FINISHED.toLong() -> Icons.Outlined.Done
                    SManga.CANCELLED.toLong() -> Icons.Outlined.Close
                    SManga.ON_HIATUS.toLong() -> Icons.Outlined.Pause
                    else -> Icons.Outlined.Block
                },
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Text(
                    text = when (status) {
                        SManga.ONGOING.toLong() -> stringResource(R.string.ongoing)
                        SManga.COMPLETED.toLong() -> stringResource(R.string.completed)
                        SManga.LICENSED.toLong() -> stringResource(R.string.licensed)
                        SManga.PUBLISHING_FINISHED.toLong() -> stringResource(R.string.publishing_finished)
                        SManga.CANCELLED.toLong() -> stringResource(R.string.cancelled)
                        SManga.ON_HIATUS.toLong() -> stringResource(R.string.on_hiatus)
                        else -> stringResource(R.string.unknown)
                    },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                DotSeparatorText()
                if (isStubSource) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = sourceName,
                    modifier = Modifier.clickableNoIndication { doSearch(sourceName, false) },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MangaAndSourceTitlesSmall(
    appBarPadding: Dp,
    coverDataProvider: () -> Manga,
    onCoverClick: () -> Unit,
    title: String,
    context: Context,
    doSearch: (query: String, global: Boolean) -> Unit,
    author: String?,
    artist: String?,
    status: Long,
    sourceName: String,
    isStubSource: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = appBarPadding + 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Book(
            modifier = Modifier
                .sizeIn(maxWidth = 100.dp)
                .align(Alignment.Top),
            data = coverDataProvider(),
            contentDescription = stringResource(R.string.manga_cover),
            onClick = onCoverClick,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title.ifBlank { stringResource(R.string.unknown_title) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickableNoIndication(
                    onLongClick = {
                        if (title.isNotBlank()) {
                            context.copyToClipboard(
                                title,
                                title,
                            )
                        }
                    },
                    onClick = { if (title.isNotBlank()) doSearch(title, true) },
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = author?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.unknown_author),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(top = 2.dp)
                    .clickableNoIndication(
                        onLongClick = {
                            if (!author.isNullOrBlank()) {
                                context.copyToClipboard(
                                    author,
                                    author,
                                )
                            }
                        },
                        onClick = { if (!author.isNullOrBlank()) doSearch(author, true) },
                    ),
            )
            if (!artist.isNullOrBlank() && author != artist) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .secondaryItemAlpha()
                        .padding(top = 2.dp)
                        .clickableNoIndication(
                            onLongClick = { context.copyToClipboard(artist, artist) },
                            onClick = { doSearch(artist, true) },
                        ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.secondaryItemAlpha(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (status) {
                        SManga.ONGOING.toLong() -> Icons.Outlined.Schedule
                        SManga.COMPLETED.toLong() -> Icons.Outlined.DoneAll
                        SManga.LICENSED.toLong() -> Icons.Outlined.AttachMoney
                        SManga.PUBLISHING_FINISHED.toLong() -> Icons.Outlined.Done
                        SManga.CANCELLED.toLong() -> Icons.Outlined.Close
                        SManga.ON_HIATUS.toLong() -> Icons.Outlined.Pause
                        else -> Icons.Outlined.Block
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp),
                )
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Text(
                        text = when (status) {
                            SManga.ONGOING.toLong() -> stringResource(R.string.ongoing)
                            SManga.COMPLETED.toLong() -> stringResource(R.string.completed)
                            SManga.LICENSED.toLong() -> stringResource(R.string.licensed)
                            SManga.PUBLISHING_FINISHED.toLong() -> stringResource(R.string.publishing_finished)
                            SManga.CANCELLED.toLong() -> stringResource(R.string.cancelled)
                            SManga.ON_HIATUS.toLong() -> stringResource(R.string.on_hiatus)
                            else -> stringResource(R.string.unknown)
                        },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    DotSeparatorText()
                    if (isStubSource) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = sourceName,
                        modifier = Modifier.clickableNoIndication {
                            doSearch(
                                sourceName,
                                false,
                            )
                        },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaSummary(
    expandedDescription: String,
    shrunkDescription: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var expandedHeight by remember { mutableIntStateOf(0) }
    var shrunkHeight by remember { mutableIntStateOf(0) }
    val heightDelta = remember(expandedHeight, shrunkHeight) { expandedHeight - shrunkHeight }
    val animProgress by animateFloatAsState(if (expanded) 1f else 0f)
    val scrimHeight = with(LocalDensity.current) { remember { 24.sp.roundToPx() } }

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val shrunkPlaceable = subcompose("description-s") {
            Text(
                text = "\n\n", // Shows at least 3 lines
                style = MaterialTheme.typography.bodyMedium,
            )
        }.map { it.measure(constraints) }
        shrunkHeight = shrunkPlaceable.maxByOrNull { it.height }?.height ?: 0

        val expandedPlaceable = subcompose("description-l") {
            Text(
                text = expandedDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
        }.map { it.measure(constraints) }
        expandedHeight = expandedPlaceable.maxByOrNull { it.height }?.height?.coerceAtLeast(shrunkHeight) ?: 0

        val actualPlaceable = subcompose("description") {
            SelectionContainer {
                Text(
                    text = if (expanded) expandedDescription else shrunkDescription,
                    maxLines = Int.MAX_VALUE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.secondaryItemAlpha(),
                )
            }
        }.map { it.measure(constraints) }

        val scrimPlaceable = subcompose("scrim") {
            val colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
            Box(
                modifier = Modifier.background(Brush.verticalGradient(colors = colors)),
                contentAlignment = Alignment.Center,
            ) {
                val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_caret_down)
                Icon(
                    painter = rememberAnimatedVectorPainter(image, !expanded),
                    contentDescription = stringResource(if (expanded) R.string.manga_info_collapse else R.string.manga_info_expand),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.background(Brush.radialGradient(colors = colors.asReversed())),
                )
            }
        }.map { it.measure(Constraints.fixed(width = constraints.maxWidth, height = scrimHeight)) }

        val currentHeight = shrunkHeight + ((heightDelta + scrimHeight) * animProgress).roundToInt()
        layout(constraints.maxWidth, currentHeight) {
            actualPlaceable.forEach {
                it.place(0, 0)
            }

            val scrimY = currentHeight - scrimHeight
            scrimPlaceable.forEach {
                it.place(0, scrimY)
            }
        }
    }
}

@Composable
private fun TagsChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        SuggestionChip(
            modifier = modifier,
            onClick = onClick,
            label = { Text(text = text, style = MaterialTheme.typography.bodySmall) },
        )
    }
}

@Composable
private fun RowScope.MangaActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        onLongClick = onLongClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
