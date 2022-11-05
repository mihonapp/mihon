package eu.kanade.presentation.updates

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.core.util.insertSeparators
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesPresenter
import eu.kanade.tachiyomi.util.lang.toDateKey
import java.util.Date

@Stable
interface UpdatesState {
    val isLoading: Boolean
    val items: List<UpdatesItem>
    val selected: List<UpdatesItem>
    val selectionMode: Boolean
    val uiModels: List<UpdatesUiModel>
    var dialog: UpdatesPresenter.Dialog?
}
fun UpdatesState(): UpdatesState = UpdatesStateImpl()
class UpdatesStateImpl : UpdatesState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var items: List<UpdatesItem> by mutableStateOf(emptyList())
    override val selected: List<UpdatesItem> by derivedStateOf {
        items.filter { it.selected }
    }
    override val selectionMode: Boolean by derivedStateOf { selected.isNotEmpty() }
    override val uiModels: List<UpdatesUiModel> by derivedStateOf {
        items.toUpdateUiModel()
    }
    override var dialog: UpdatesPresenter.Dialog? by mutableStateOf(null)
}

fun List<UpdatesItem>.toUpdateUiModel(): List<UpdatesUiModel> {
    return this.map {
        UpdatesUiModel.Item(it)
    }
        .insertSeparators { before, after ->
            val beforeDate = before?.item?.update?.dateFetch?.toDateKey() ?: Date(0)
            val afterDate = after?.item?.update?.dateFetch?.toDateKey() ?: Date(0)
            when {
                beforeDate.time != afterDate.time && afterDate.time != 0L ->
                    UpdatesUiModel.Header(afterDate)
                // Return null to avoid adding a separator between two items.
                else -> null
            }
        }
}
