package eu.kanade.presentation.more.settings.database.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.Source
import eu.kanade.presentation.components.Divider
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.more.settings.database.ClearDatabaseState
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseContent(
    state: ClearDatabaseState,
    contentPadding: PaddingValues,
    onClickSelection: (Source) -> Unit,
    onClickDelete: () -> Unit,
) {
    Crossfade(targetState = state.isEmpty.not()) { _state ->
        when (_state) {
            true -> {
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxSize(),
                ) {
                    FastScrollLazyColumn(
                        modifier = Modifier.weight(1f),
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

                    Divider()

                    Button(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        onClick = onClickDelete,
                        enabled = state.selection.isNotEmpty(),
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            false -> {
                EmptyScreen(message = stringResource(R.string.database_clean))
            }
        }
    }
}
