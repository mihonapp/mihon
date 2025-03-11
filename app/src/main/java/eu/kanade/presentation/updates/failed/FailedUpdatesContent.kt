package eu.kanade.presentation.updates.failed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun LazyListScope.failedUpdatesUiItems(
    items: List<FailedUpdatesManga>,
    selectionMode: Boolean,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    onClick: (FailedUpdatesManga) -> Unit,
    groupingMode: GroupByMode,
) {
    items(
        items = items,
        key = { it.libraryManga.manga.id },
    ) { item ->
        Box(modifier = Modifier.animateItemPlacement(animationSpec = tween(300))) {
            FailedUpdatesUiItem(
                modifier = Modifier,
                selected = item.selected,
                onLongClick = {
                    onSelected(item, !item.selected, true, true)
                },
                onClick = {
                    when {
                        selectionMode -> onSelected(item, !item.selected, true, false)
                        else -> onClick(item)
                    }
                },
                manga = item,
                groupingMode = groupingMode,
            )
        }
    }
}

@Composable
private fun FailedUpdatesUiItem(
    modifier: Modifier,
    manga: FailedUpdatesManga,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    groupingMode: GroupByMode = GroupByMode.BY_SOURCE,
) {
    val haptic = LocalHapticFeedback.current
    val textAlpha = 1f
    Row(
        modifier = modifier
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    onLongClick()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
            .height(56.dp)
            .padding(start = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = manga.libraryManga.manga,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f)
                .animateContentSize(),
        ) {
            Text(
                text = manga.libraryManga.manga.title,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = textAlpha),
                overflow = TextOverflow.Ellipsis,
            )
            if (groupingMode == GroupByMode.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var textHeight by remember { mutableIntStateOf(0) }
                    Text(
                        text = manga.simplifiedErrorMessage,
                        maxLines = if (selected) Int.MAX_VALUE else 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        onTextLayout = { textHeight = it.size.height },
                        modifier = Modifier
                            .weight(weight = 1f, fill = false),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

fun returnSourceIcon(id: Long): ImageBitmap? {
    return Injekt.get<ExtensionManager>().getAppIconForSource(id)
        ?.toBitmap()
        ?.asImageBitmap()
}

fun LazyListScope.failedUpdatesGroupUiItem(
    errorMessageMap: Map<Pair<String, String>, List<FailedUpdatesManga>>,
    selectionMode: Boolean,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    onMangaClick: (FailedUpdatesManga) -> Unit,
    id: String,
    onGroupSelected: (List<FailedUpdatesManga>) -> Unit,
    onExpandedMapChange: (GroupKey, Boolean) -> Unit,
    expanded: Map<GroupKey, Boolean>,
    showLanguageInContent: Boolean = true,
    sourcesCount: List<Pair<Source, Long>>,
    onClickIcon: (String) -> Unit = {},
    onLongClickIcon: (String) -> Unit = {},
) {
    item(
        key = errorMessageMap.values.flatten().find { it.source.name == id }!!.source.id,
    ) {
        ElevatedCard(
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
            shape = RoundedCornerShape(corner = CornerSize(15.dp)),
            modifier = Modifier
                .padding(vertical = 9.dp)
                .animateItemPlacement(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                )
                .fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectedBackground(
                            !errorMessageMap.values
                                .flatten()
                                .fastAny { !it.selected },
                        )
                        .combinedClickable(
                            onClick = {
                                val categoryKey = GroupKey(id, Pair("", ""))
                                if (!expanded.containsKey(categoryKey)) {
                                    onExpandedMapChange(categoryKey, true)
                                }
                                onExpandedMapChange(categoryKey, !expanded[categoryKey]!!)
                            },
                            onLongClick = { onGroupSelected(errorMessageMap.values.flatten()) },
                        )
                        .padding(
                            horizontal = 12.dp,
                            vertical = 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val item = errorMessageMap.values.flatten().find { it.source.name == id }!!.source
                    val sourceLangString =
                        LocaleHelper.getSourceDisplayName(item.lang, LocalContext.current)
                            .takeIf { showLanguageInContent }
                    val icon = returnSourceIcon(item.id)
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .height(50.dp)
                                .aspectRatio(1f),
                        )
                    } else {
                        Image(
                            imageVector = Icons.Filled.Dangerous,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .height(50.dp)
                                .aspectRatio(1f),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .weight(1f),
                    ) {
                        Text(
                            text = item.name.ifBlank { item.id.toString() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.15.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sourceLangString != null) {
                                Text(
                                    modifier = Modifier.secondaryItemAlpha(),
                                    text = sourceLangString,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    val mangaCount = errorMessageMap.values.flatten().size
                    val sourceCount = sourcesCount.find { it.first.id == item.id }!!.second
                    Pill(
                        text = "$mangaCount/$sourceCount",
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        style = LocalTextStyle.current,
                    )
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded[GroupKey(id, Pair("", ""))] == true) 0f else -180f,
                        animationSpec = tween(500),
                        label = "",
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        modifier = Modifier
                            .rotate(rotation)
                            .padding(vertical = 8.dp, horizontal = 14.dp),
                        contentDescription = null,
                    )
                }
                Column {
                    errorMessageMap.forEach { (errorMessagePair, items) ->
                        val errorMessageHeaderId = GroupKey(id, errorMessagePair)
                        AnimatedVisibility(
                            modifier = Modifier,
                            visible = expanded[GroupKey(id, Pair("", ""))] == true,
                        ) {
                            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectedBackground(!items.fastAny { !it.selected })
                                    .combinedClickable(
                                        onClick =
                                        {
                                            if (expanded[errorMessageHeaderId] == null) {
                                                onExpandedMapChange(errorMessageHeaderId, true)
                                            } else {
                                                onExpandedMapChange(
                                                    errorMessageHeaderId,
                                                    !expanded[errorMessageHeaderId]!!,
                                                )
                                            }
                                        },
                                        onLongClick = { onGroupSelected(items) },
                                    )
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 12.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CustomIconButton(
                                    onClick = {
                                        onClickIcon(
                                            errorMessagePair.first,
                                        )
                                    },
                                    onLongClick = {
                                        onLongClickIcon(
                                            errorMessagePair.first,
                                        )
                                    },
                                    modifier = Modifier,
                                    content = {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = "",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = MaterialTheme.padding.medium)
                                        .weight(1f),
                                ) {
                                    Text(
                                        errorMessagePair.second.ifEmpty {
                                            errorMessagePair.first.substringAfter(":").substring(1)
                                        },
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.error,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        lineHeight = 24.sp,
                                        letterSpacing = 0.15.sp,
                                    )
                                }
                                val rotation by animateFloatAsState(
                                    targetValue = if (expanded[errorMessageHeaderId] == true) 0f else -180f,
                                    animationSpec = tween(500),
                                    label = "",
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(
                                            top = 8.dp,
                                            bottom = 8.dp,
                                            start = 10.dp,
                                            end = 14.1.dp,
                                        )
                                        .rotate(rotation),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                        Column {
                            items.forEachIndexed { index, item ->
                                val isLastItem = index == items.lastIndex
                                AnimatedVisibility(
                                    modifier = Modifier,
                                    visible =
                                    expanded[errorMessageHeaderId] == true &&
                                        expanded[GroupKey(id, Pair("", ""))] == true,
                                ) {
                                    FailedUpdatesUiItem(
                                        modifier = Modifier
                                            .padding(bottom = if (isLastItem) 5.dp else 0.dp),
                                        selected = item.selected,
                                        onLongClick = {
                                            onSelected(item, !item.selected, true, true)
                                        },
                                        onClick = {
                                            when {
                                                selectionMode -> onSelected(
                                                    item,
                                                    !item.selected,
                                                    true,
                                                    false,
                                                )

                                                else -> onMangaClick(item)
                                            }
                                        },
                                        manga = item,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = remember {
                    ripple(
                        bounded = false,
                        radius = 20.dp,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(content = content)
    }
}

@Composable
fun CategoryList(
    contentPadding: PaddingValues,
    selectionMode: Boolean,
    onMangaClick: (FailedUpdatesManga) -> Unit,
    onGroupSelected: (List<FailedUpdatesManga>) -> Unit,
    onSelected: (FailedUpdatesManga, Boolean, Boolean, Boolean) -> Unit,
    categoryMap: Map<String, Map<Pair<String, String>, List<FailedUpdatesManga>>>,
    onExpandedMapChange: (GroupKey, Boolean) -> Unit,
    expanded: Map<GroupKey, Boolean>,
    sourcesCount: List<Pair<Source, Long>>,
    onClickIcon: (String) -> Unit = {},
    onLongClickIcon: (String) -> Unit = {},
    lazyListState: LazyListState,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 10.dp),
        state = lazyListState,
    ) {
        categoryMap.forEach { (category, errorMessageMap) ->
            failedUpdatesGroupUiItem(
                id = category,
                errorMessageMap = errorMessageMap,
                selectionMode = selectionMode,
                onMangaClick = onMangaClick,
                onSelected = onSelected,
                onGroupSelected = onGroupSelected,
                onExpandedMapChange = onExpandedMapChange,
                expanded = expanded,
                sourcesCount = sourcesCount,
                onClickIcon = onClickIcon,
                onLongClickIcon = onLongClickIcon,
            )
        }
    }
}
