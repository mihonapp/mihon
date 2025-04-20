package eu.kanade.tachiyomi.ui.browse.source.blockrule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.blockrule.components.BlockruleCreateDialog
import eu.kanade.tachiyomi.ui.browse.source.blockrule.components.BlockruleDeleteDialog
import eu.kanade.tachiyomi.ui.browse.source.blockrule.components.BlockruleScreenC
import eu.kanade.tachiyomi.ui.browse.source.blockrule.components.BlockruleSortAlphabeticallyDialog
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.LoadingScreen

class BlockruleScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { BlockruleScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is BlockruleScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as BlockruleScreenState.Success

        BlockruleScreenC(
            state = successState,
            onClickCreate = { screenModel.showDialog(BlockruleDialog.Create) },
            onClickSortAlphabetically = { screenModel.showDialog(BlockruleDialog.SortAlphabetically) },
            onClickEdit = { screenModel.showDialog(BlockruleDialog.Edit(it)) },
            onClickEnable = { b, e -> screenModel.enableBlockrule(b, e) },
            onClickDelete = { screenModel.showDialog(BlockruleDialog.Delete(it)) },
            onClickMoveUp = screenModel::moveUp,
            onClickMoveDown = screenModel::moveDown,
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null                                  -> {}
            is BlockruleDialog.Create             -> {
                BlockruleCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onConfirm = screenModel::createBlockrule,
                    blockrules = successState.blockrules,
                    title = tachiyomi.presentation.core.i18n.stringResource(MR.strings.block_rule_add_block_rule),
                )
            }

            is BlockruleDialog.Delete             -> {
                BlockruleDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteBlockrule(dialog.blockrule.id) },
                    blockrule = dialog.blockrule,
                )
            }

            is BlockruleDialog.SortAlphabetically -> {
                BlockruleSortAlphabeticallyDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onSort = screenModel::sortAlphabetically,
                )
            }

            is BlockruleDialog.Edit               -> {
                val edit = dialog.blockrule
                BlockruleCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onConfirm = { name, type, rule ->
                        screenModel.editBlockrule(edit, name = name, type, rule)
                    },
                    blockrules = successState.blockrules,
                    editBlockrule = edit,
                    title = tachiyomi.presentation.core.i18n.stringResource(MR.strings.block_rule_edit_block_rule),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is BlockruleEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
