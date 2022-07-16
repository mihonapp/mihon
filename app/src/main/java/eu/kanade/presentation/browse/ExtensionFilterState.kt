package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.browse.extension.FilterUiModel

@Stable
interface ExtensionFilterState {
    val isLoading: Boolean
    val items: List<FilterUiModel>
    val isEmpty: Boolean
}

fun ExtensionFilterState(): ExtensionFilterState {
    return ExtensionFilterStateImpl()
}

class ExtensionFilterStateImpl : ExtensionFilterState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var items: List<FilterUiModel> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { items.isEmpty() }
}
