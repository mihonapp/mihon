package eu.kanade.presentation.browse

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel

interface ExtensionsState {
    val isLoading: Boolean
    val isRefreshing: Boolean
    val items: List<ExtensionUiModel>
    val updates: Int
    val isEmpty: Boolean
}

fun ExtensionState(): ExtensionsState {
    return ExtensionsStateImpl()
}

class ExtensionsStateImpl : ExtensionsState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var isRefreshing: Boolean by mutableStateOf(false)
    override var items: List<ExtensionUiModel> by mutableStateOf(emptyList())
    override var updates: Int by mutableStateOf(0)
    override val isEmpty: Boolean by derivedStateOf { items.isEmpty() }
}
