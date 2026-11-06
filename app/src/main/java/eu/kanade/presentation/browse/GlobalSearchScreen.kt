package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import eu.kanade.presentation.browse.components.GlobalSearchCardRow
import eu.kanade.presentation.browse.components.GlobalSearchErrorResultItem
import eu.kanade.presentation.browse.components.GlobalSearchLoadingResultItem
import eu.kanade.presentation.browse.components.GlobalSearchResultItem
import eu.kanade.presentation.browse.components.GlobalSearchToolbar
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold

@Composable
fun GlobalSearchScreen(
    state: SearchViewModel.State,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    onToggleChapterCountSort: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            GlobalSearchToolbar(
                searchQuery = state.searchQuery,
                progress = state.progress,
                total = state.total,
                navigateUp = navigateUp,
                onChangeSearchQuery = onChangeSearchQuery,
                onSearch = onSearch,
                hideSourceFilter = false,
                sourceFilter = state.sourceFilter,
                onChangeSearchFilter = onChangeSearchFilter,
                onlyShowHasResults = state.onlyShowHasResults,
                onToggleResults = onToggleResults,
                sortByChapterCount = state.sortByChapterCount,
                onToggleChapterCountSort = onToggleChapterCountSort,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            chapterCounts = state.chapterCounts,
            chapterCountIds = state.chapterCountIds,
            sortByChapterCount = state.sortByChapterCount,
            contentPadding = paddingValues,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: Map<Source, SearchItemResult>,
    chapterCounts: Map<Long, Int>,
    chapterCountIds: Set<Long>,
    sortByChapterCount: Boolean,
    contentPadding: PaddingValues,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    fromSourceId: Long? = null,
) {
    val orderedItems = if (sortByChapterCount) {
        items.entries.sortedWith { left, right ->
            val leftMax = left.value.maxChapterCount(chapterCounts)
            val rightMax = right.value.maxChapterCount(chapterCounts)
            when {
                leftMax == null && rightMax == null -> 0
                leftMax == null -> 1
                rightMax == null -> -1
                leftMax != rightMax -> rightMax.compareTo(leftMax)
                else -> 0
            }
        }
    } else {
        items.entries.toList()
    }

    LazyColumn(
        contentPadding = contentPadding,
    ) {
        orderedItems.forEach { (source, result) ->
            item(key = source.id) {
                GlobalSearchResultItem(
                    title = fromSourceId?.let {
                        "▶ ${source.name}".takeIf { source.id == fromSourceId }
                    } ?: source.name,
                    subtitle = LocaleHelper.getLocalizedDisplayName(source.lang),
                    onClick = { onClickSource(source) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (result) {
                        SearchItemResult.Loading -> {
                            GlobalSearchLoadingResultItem()
                        }
                        is SearchItemResult.Success -> {
                            val titles = if (sortByChapterCount) {
                                result.result.sortedByChapterCount(chapterCounts)
                            } else {
                                result.result
                            }
                            GlobalSearchCardRow(
                                titles = titles,
                                chapterCounts = chapterCounts,
                                chapterCountIds = chapterCountIds,
                                showChapterCounts = sortByChapterCount,
                                getManga = getManga,
                                onClick = onClickItem,
                                onLongClick = onLongClickItem,
                            )
                        }
                        is SearchItemResult.Error -> {
                            GlobalSearchErrorResultItem(message = result.throwable.message)
                        }
                    }
                }
            }
        }
    }
}

private fun SearchItemResult.maxChapterCount(chapterCounts: Map<Long, Int>): Int? {
    val success = this as? SearchItemResult.Success ?: return null
    return success.result.mapNotNull { manga ->
        chapterCounts[manga.id]?.takeIf { count -> count >= 0 }
    }.maxOrNull()
}

private fun List<Manga>.sortedByChapterCount(chapterCounts: Map<Long, Int>): List<Manga> {
    return sortedWith { left, right ->
        val leftCount = chapterCounts[left.id]?.takeIf { it >= 0 }
        val rightCount = chapterCounts[right.id]?.takeIf { it >= 0 }
        when {
            leftCount == null && rightCount == null -> 0
            leftCount == null -> 1
            rightCount == null -> -1
            leftCount != rightCount -> rightCount.compareTo(leftCount)
            else -> left.title.compareTo(right.title)
        }
    }
}
