package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class LibraryNavigation {
    var pageInfo: LibraryPageInfo? by mutableStateOf(null)
}

data class LibraryPageInfo(
    val index: Int,
    val count: Int,
    val maxRows: Int = 10,
    val canStretchSpacing: Boolean = false,
)
