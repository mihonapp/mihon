package eu.kanade.presentation.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionSourceItem

@Stable
interface ExtensionDetailsState {
    val isLoading: Boolean
    val extension: Extension.Installed?
    val sources: List<ExtensionSourceItem>
}

fun ExtensionDetailsState(): ExtensionDetailsState {
    return ExtensionDetailsStateImpl()
}

class ExtensionDetailsStateImpl : ExtensionDetailsState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var extension: Extension.Installed? by mutableStateOf(null)
    override var sources: List<ExtensionSourceItem> by mutableStateOf(emptyList())
}
