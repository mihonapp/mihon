package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.browse.source.SourcesPresenter

@Stable
interface SourcesState {
    var dialog: SourcesPresenter.Dialog?
    val isLoading: Boolean
    val items: List<SourceUiModel>
    val isEmpty: Boolean
}

fun SourcesState(): SourcesState {
    return SourcesStateImpl()
}

class SourcesStateImpl : SourcesState {
    override var dialog: SourcesPresenter.Dialog? by mutableStateOf(null)
    override var isLoading: Boolean by mutableStateOf(true)
    override var items: List<SourceUiModel> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { items.isEmpty() }
}
