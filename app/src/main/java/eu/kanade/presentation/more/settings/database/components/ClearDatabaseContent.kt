package eu.kanade.presentation.more.settings.database.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.more.settings.database.ClearDatabaseState
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseContent(
    state: ClearDatabaseState,
    contentPadding: PaddingValues,
    onClickSelection: (Source) -> Unit,
) {
    Crossfade(targetState = state.isEmpty.not()) { _state ->
        when (_state) {
            true -> FastScrollLazyColumn(
                contentPadding = contentPadding + WindowInsets.navigationBars.asPaddingValues(),
            ) {
                items(state.items) { sourceWithCount ->
                    ClearDatabaseItem(
                        source = sourceWithCount.source,
                        count = sourceWithCount.count,
                        isSelected = state.selection.contains(sourceWithCount.id),
                        onClickSelect = { onClickSelection(sourceWithCount.source) },
                    )
                }
            }
            false -> EmptyScreen(message = stringResource(id = R.string.database_clean))
        }
    }
}
