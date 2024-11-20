package mihon.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun <T: Any> StateFlow<Flow<PagingData<T>>>.collectAsLazyPagingItems(): LazyPagingItems<T> {
    val flow by collectAsState()
    return flow.collectAsLazyPagingItems()
}
