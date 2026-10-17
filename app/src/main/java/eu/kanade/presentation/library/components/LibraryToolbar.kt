package eu.kanade.presentation.library.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.materialsymbols.rounded.Search
import mihon.icons.materialsymbols.rounded.SelectAll
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun LibraryToolbar(
    hasActiveFilters: Boolean,
    selectedCount: Int,
    title: LibraryToolbarTitle,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomManga: () -> Unit,
    onClickProfile: () -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) = when {
    selectedCount > 0 -> LibrarySelectionToolbar(
        selectedCount = selectedCount,
        onClickUnselectAll = onClickUnselectAll,
        onClickSelectAll = onClickSelectAll,
        onClickInvertSelection = onClickInvertSelection,
    )
    else -> LibraryRegularToolbar(
        title = title,
        hasFilters = hasActiveFilters,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onClickFilter = onClickFilter,
        onClickRefresh = onClickRefresh,
        onClickGlobalUpdate = onClickGlobalUpdate,
        onClickOpenRandomManga = onClickOpenRandomManga,
        onClickProfile = onClickProfile,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LibraryRegularToolbar(
    title: LibraryToolbarTitle,
    hasFilters: Boolean,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomManga: () -> Unit,
    onClickProfile: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
    if (searchQuery == null) {
        LibraryWelcomeHeader(
            hasFilters = hasFilters,
            onOpenSearch = { onSearchQueryChange("") },
            onClickFilter = onClickFilter,
            onClickGlobalUpdate = onClickGlobalUpdate,
            onClickRefresh = onClickRefresh,
            onClickOpenRandomManga = onClickOpenRandomManga,
            onClickProfile = onClickProfile,
        )
    } else SearchToolbar(
        titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, false),
                    overflow = TextOverflow.Ellipsis,
                )
                if (title.numberOfManga != null) {
                    Pill(
                        text = "${title.numberOfManga}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                        fontSize = 14.sp,
                    )
                }
            }
        },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        actions = {
            val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
            AppBarActions(
                listOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_filter),
                        icon = MaterialSymbols.Rounded.FilterList,
                        iconTint = filterTint,
                        onClick = onClickFilter,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_library),
                        onClick = onClickGlobalUpdate,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_category),
                        onClick = onClickRefresh,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_open_random_manga),
                        onClick = onClickOpenRandomManga,
                    ),
                ),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LibraryWelcomeHeader(
    hasFilters: Boolean,
    onOpenSearch: () -> Unit,
    onClickFilter: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickOpenRandomManga: () -> Unit,
    onClickProfile: () -> Unit,
) {
    val context = LocalContext.current
    val avatarUri = remember(context) {
        context.getSharedPreferences("afraga_profile", android.content.Context.MODE_PRIVATE)
            .getString("avatar_uri", null)
    }
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF182035))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onClickProfile),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Open profile",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Person,
                    contentDescription = "Open profile",
                    tint = Color(0xFFB388FF),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy((-2).dp),
        ) {
            Text(
                text = "WELCOME",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "BACK",
                color = Color(0xFF00E5FF),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.8.sp,
            )
        }
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                icon = MaterialSymbols.Rounded.Search,
                description = stringResource(MR.strings.action_search),
                tint = Color(0xFF00E5FF),
                onClick = onOpenSearch,
            )
            HeaderIconButton(
                icon = MaterialSymbols.Rounded.FilterList,
                description = stringResource(MR.strings.action_filter),
                tint = Color(0xFF007BFF),
                showBadge = hasFilters,
                onClick = onClickFilter,
            )
            Box {
                HeaderIconButton(
                    icon = MaterialSymbols.Rounded.MoreVert,
                    description = stringResource(MR.strings.label_more),
                    tint = Color(0xFFD500F9),
                    onClick = { overflowExpanded = true },
                )
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_update_library)) },
                        onClick = { overflowExpanded = false; onClickGlobalUpdate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_update_category)) },
                        onClick = { overflowExpanded = false; onClickRefresh() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_open_random_manga)) },
                        onClick = { overflowExpanded = false; onClickOpenRandomManga() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xBF161F33))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(imageVector = icon, contentDescription = description, tint = tint)
        }
        if (showBadge) {
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676)),
            )
        }
    }
}

@Composable
private fun LibrarySelectionToolbar(
    selectedCount: Int,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                listOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = MaterialSymbols.Rounded.SelectAll,
                        onClick = onClickSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = MaterialSymbols.Rounded.FlipToBack,
                        onClick = onClickInvertSelection,
                    ),
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onClickUnselectAll,
    )
}

@Immutable
data class LibraryToolbarTitle(
    val text: String,
    val numberOfManga: Int? = null,
)
