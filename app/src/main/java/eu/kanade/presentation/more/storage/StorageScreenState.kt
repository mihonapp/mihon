package eu.kanade.presentation.more.storage

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.more.storage.data.StorageData
import tachiyomi.domain.category.model.Category

sealed interface StorageScreenState {
    data class Loading(
        val progress: Int,
    ) : StorageScreenState

    @Immutable
    data class Success(
        val items: List<StorageData>,
        val categories: List<Category>,
    ) : StorageScreenState
}
