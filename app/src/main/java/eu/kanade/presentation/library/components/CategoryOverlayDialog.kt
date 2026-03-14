package eu.kanade.presentation.library.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

enum class CategoryOverlayDisplayMode {
    List,
    Nested,
    Thumbnail,
}

data class CategoryGroup(
    val parentName: String,
    val children: List<CategoryChild>,
    val isTopLevel: Boolean,
)

data class CategoryChild(
    val category: Category,
    val childName: String,
    val originalIndex: Int,
)

fun buildCategoryGroups(categories: List<Category>): List<CategoryGroup> {
    val groups = mutableListOf<CategoryGroup>()
    val groupMap = mutableMapOf<String, MutableList<CategoryChild>>()

    categories.forEachIndexed { index, category ->
        val name = category.name
        val slashIndex = name.indexOf('/')

        if (slashIndex > 0 && slashIndex < name.length - 1) {
            val parentName = name.substring(0, slashIndex).trim()
            val childName = name.substring(slashIndex + 1).trim()
            if (parentName.isNotEmpty() && childName.isNotEmpty()) {
                groupMap.getOrPut(parentName) { mutableListOf() }
                    .add(CategoryChild(category, childName, index))
            }
        }
    }

    val usedGroups = mutableSetOf<String>()

    categories.forEachIndexed { index, category ->
        val name = category.name
        val slashIndex = name.indexOf('/')

        if (slashIndex > 0 && slashIndex < name.length - 1) {
            val parentName = name.substring(0, slashIndex).trim()
            if (parentName.isNotEmpty() && parentName !in usedGroups) {
                val children = groupMap[parentName]
                if (children != null && children.size > 1) {
                    groups.add(
                        CategoryGroup(
                            parentName = parentName,
                            children = children,
                            isTopLevel = false,
                        ),
                    )
                } else if (children != null) {
                    groups.add(
                        CategoryGroup(
                            parentName = children[0].category.name,
                            children = children,
                            isTopLevel = true,
                        ),
                    )
                }
                usedGroups.add(parentName)
            }
        } else {
            val alreadyAdded = groups.any { g ->
                g.isTopLevel && g.children.any { it.originalIndex == index }
            }
            if (!alreadyAdded) {
                groups.add(
                    CategoryGroup(
                        parentName = name,
                        children = listOf(
                            CategoryChild(category, name, index),
                        ),
                        isTopLevel = true,
                    ),
                )
            }
        }
    }

    return groups
}

@Composable
fun CategoryOverlayDialog(
    visible: Boolean,
    categories: List<Category>,
    currentCategoryIndex: Int,
    getItemCountForCategory: (Category) -> Int?,
    getFirstMangaCoverForCategory: (Category) -> MangaCover?,
    onCategorySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var displayMode by rememberSaveable {
        mutableStateOf(CategoryOverlayDisplayMode.List)
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(visible, currentCategoryIndex) {
        if (visible && currentCategoryIndex >= 0) {
            gridState.scrollToItem(currentCategoryIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxSize(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                CategoryOverlayHeader(
                    displayMode = displayMode,
                    onDisplayModeChange = { displayMode = it },
                    onDismiss = onDismiss,
                    categoryCount = categories.size,
                )

                HorizontalDivider()

                when (displayMode) {
                    CategoryOverlayDisplayMode.Nested -> {
                        CategoryNestedList(
                            categories = categories,
                            currentCategoryIndex = currentCategoryIndex,
                            getItemCountForCategory = getItemCountForCategory,
                            onCategorySelected = onCategorySelected,
                            onDismiss = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CategoryOverlayDisplayMode.List -> {
                        CategoryFlatGrid(
                            categories = categories,
                            currentCategoryIndex = currentCategoryIndex,
                            gridState = gridState,
                            getItemCountForCategory = getItemCountForCategory,
                            onCategorySelected = onCategorySelected,
                            onDismiss = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CategoryOverlayDisplayMode.Thumbnail -> {
                        CategoryThumbnailGrid(
                            categories = categories,
                            currentCategoryIndex = currentCategoryIndex,
                            gridState = gridState,
                            getItemCountForCategory = getItemCountForCategory,
                            getFirstMangaCoverForCategory = getFirstMangaCoverForCategory,
                            onCategorySelected = onCategorySelected,
                            onDismiss = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MR.strings.category_count, categories.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryNestedList(
    categories: List<Category>,
    currentCategoryIndex: Int,
    getItemCountForCategory: (Category) -> Int?,
    onCategorySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(categories) { buildCategoryGroups(categories) }
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            if (group.isTopLevel) {
                val child = group.children.first()
                val isSelected = child.originalIndex == currentCategoryIndex
                val itemCount = getItemCountForCategory(child.category)

                item(key = "top_${child.originalIndex}") {
                    CategoryNestedChildItem(
                        name = child.childName,
                        isSelected = isSelected,
                        itemCount = itemCount,
                        indented = false,
                        onClick = {
                            onCategorySelected(child.originalIndex)
                            onDismiss()
                        },
                    )
                }
            } else {
                val isExpanded = expandedState[group.parentName] ?: false
                val totalItemCount = group.children.sumOf { child ->
                    getItemCountForCategory(child.category) ?: 0
                }
                val hasSelectedChild = group.children.any {
                    it.originalIndex == currentCategoryIndex
                }

                item(key = "header_${group.parentName}") {
                    CategoryNestedGroupHeader(
                        parentName = group.parentName,
                        childCount = group.children.size,
                        totalItemCount = totalItemCount,
                        isExpanded = isExpanded,
                        hasSelectedChild = hasSelectedChild,
                        onClick = {
                            expandedState[group.parentName] = !isExpanded
                        },
                    )
                }

                if (isExpanded) {
                    group.children.forEach { child ->
                        val isSelected = child.originalIndex == currentCategoryIndex
                        val itemCount = getItemCountForCategory(child.category)

                        item(key = "child_${child.originalIndex}") {
                            CategoryNestedChildItem(
                                name = child.childName,
                                isSelected = isSelected,
                                itemCount = itemCount,
                                indented = true,
                                onClick = {
                                    onCategorySelected(child.originalIndex)
                                    onDismiss()
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
private fun CategoryNestedGroupHeader(
    parentName: String,
    childCount: Int,
    totalItemCount: Int,
    isExpanded: Boolean,
    hasSelectedChild: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (hasSelectedChild) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = parentName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (hasSelectedChild) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text = "($childCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "$totalItemCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CategoryNestedChildItem(
    name: String,
    isSelected: Boolean,
    itemCount: Int?,
    indented: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 28.dp else 0.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (itemCount != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($itemCount)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun CategoryFlatGrid(
    categories: List<Category>,
    currentCategoryIndex: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    getItemCountForCategory: (Category) -> Int?,
    onCategorySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = categories,
            key = { it.id },
        ) { category ->
            val index = categories.indexOf(category)
            val isSelected = index == currentCategoryIndex
            val itemCount = getItemCountForCategory(category)

            CategoryListItem(
                category = category,
                isSelected = isSelected,
                itemCount = itemCount,
                onClick = {
                    onCategorySelected(index)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun CategoryThumbnailGrid(
    categories: List<Category>,
    currentCategoryIndex: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    getItemCountForCategory: (Category) -> Int?,
    getFirstMangaCoverForCategory: (Category) -> MangaCover?,
    onCategorySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = categories,
            key = { it.id },
        ) { category ->
            val index = categories.indexOf(category)
            val isSelected = index == currentCategoryIndex
            val itemCount = getItemCountForCategory(category)
            val cover = getFirstMangaCoverForCategory(category)

            CategoryThumbnailItem(
                category = category,
                isSelected = isSelected,
                itemCount = itemCount,
                coverData = cover,
                onClick = {
                    onCategorySelected(index)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun CategoryOverlayHeader(
    displayMode: CategoryOverlayDisplayMode,
    onDisplayModeChange: (CategoryOverlayDisplayMode) -> Unit,
    onDismiss: () -> Unit,
    categoryCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(MR.strings.action_select_category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        CategoryModeIconButton(
            icon = Icons.AutoMirrored.Filled.ViewList,
            contentDescription = stringResource(MR.strings.action_list_view),
            isSelected = displayMode == CategoryOverlayDisplayMode.List,
            onClick = { onDisplayModeChange(CategoryOverlayDisplayMode.List) },
        )

        CategoryModeIconButton(
            icon = Icons.Default.AccountTree,
            contentDescription = stringResource(MR.strings.action_nested_view),
            isSelected = displayMode == CategoryOverlayDisplayMode.Nested,
            onClick = { onDisplayModeChange(CategoryOverlayDisplayMode.Nested) },
        )

        CategoryModeIconButton(
            icon = Icons.Default.GridView,
            contentDescription = stringResource(MR.strings.action_thumbnail_view),
            isSelected = displayMode == CategoryOverlayDisplayMode.Thumbnail,
            onClick = { onDisplayModeChange(CategoryOverlayDisplayMode.Thumbnail) },
        )

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(MR.strings.action_close),
            )
        }
    }
}

@Composable
private fun CategoryModeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = if (isSelected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CategoryListItem(
    category: Category,
    isSelected: Boolean,
    itemCount: Int?,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = category.visualName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (itemCount != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($itemCount)",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun CategoryThumbnailItem(
    category: Category,
    isSelected: Boolean,
    itemCount: Int?,
    coverData: MangaCover?,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (coverData != null) {
                    AsyncImage(
                        model = coverData,
                        contentDescription = category.visualName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = category.visualName.take(2),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            ),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = category.visualName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )

                if (itemCount != null) {
                    Text(
                        text = stringResource(MR.strings.category_item_count, itemCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
