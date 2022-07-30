package eu.kanade.presentation.updates

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.recent.updates.UpdatesPresenter

@Stable
interface UpdatesState {
    val isLoading: Boolean
    val uiModels: List<UpdatesUiModel>
    val selected: List<UpdatesUiModel.Item>
    val selectionMode: Boolean
    var dialog: UpdatesPresenter.Dialog?
}
fun UpdatesState(): UpdatesState = UpdatesStateImpl()
class UpdatesStateImpl : UpdatesState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var uiModels: List<UpdatesUiModel> by mutableStateOf(emptyList())
    override val selected: List<UpdatesUiModel.Item> by derivedStateOf {
        uiModels.filterIsInstance<UpdatesUiModel.Item>()
            .filter { it.item.selected }
    }
    override val selectionMode: Boolean by derivedStateOf { selected.isNotEmpty() }
    override var dialog: UpdatesPresenter.Dialog? by mutableStateOf(null)
}
