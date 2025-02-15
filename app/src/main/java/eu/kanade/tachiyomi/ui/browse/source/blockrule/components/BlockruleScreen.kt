package eu.kanade.tachiyomi.ui.browse.source.blockrule.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.ui.browse.source.blockrule.BlockruleScreenState
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun BlockruleScreenC(
    state: BlockruleScreenState.Success,
    onClickCreate: () -> Unit,
    onClickSortAlphabetically: () -> Unit,
    onClickEdit: (Blockrule) -> Unit,
    onClickEnable: (Blockrule, Boolean) -> Unit,
    onClickDelete: (Blockrule) -> Unit,
    onClickMoveUp: (Blockrule) -> Unit,
    onClickMoveDown: (Blockrule) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.block_rule_manage),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.action_sort),
                                icon = Icons.Outlined.SortByAlpha,
                                onClick = onClickSortAlphabetically,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            BlockruleFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.block_rule_empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        BlockruleContent(
            blockrules = state.blockrules,
            lazyListState = lazyListState,
            paddingValues = paddingValues +
                topSmallPaddingValues +
                PaddingValues(horizontal = MaterialTheme.padding.medium),
            onClickEdit = onClickEdit,
            onClickEnable = onClickEnable,
            onClickDelete = onClickDelete,
            onMoveUp = onClickMoveUp,
            onMoveDown = onClickMoveDown,
        )
    }
}

@Composable
private fun BlockruleContent(
    blockrules: List<Blockrule>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickEdit: (Blockrule) -> Unit,
    onClickEnable: (Blockrule, Boolean) -> Unit,
    onClickDelete: (Blockrule) -> Unit,
    onMoveUp: (Blockrule) -> Unit,
    onMoveDown: (Blockrule) -> Unit,
) {
    LazyColumn(
        state = lazyListState,
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        itemsIndexed(
            items = blockrules,
            key = { _, blockrule -> "blockrule-${blockrule.id}" },
        ) { index, blockrule ->
            BlockruleListItem(
                modifier = Modifier.animateItem(),
                blockrule = blockrule,
                canMoveUp = index != 0,
                canMoveDown = index != blockrules.lastIndex,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onEdit = { onClickEdit(blockrule) },
                onEnable = { onClickEnable(blockrule, it) },
                onDelete = { onClickDelete(blockrule) },
                enable = blockrule.enable,
            )
        }
    }
}
