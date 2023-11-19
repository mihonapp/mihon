package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DisabledByDefault
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.isScrolledToEnd
import tachiyomi.presentation.core.util.isScrolledToStart

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: Set<String>,
    excludedScanlators: Set<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sortedAvailableScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val mutableExcludedScanlators = remember(excludedScanlators) { excludedScanlators.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.exclude_scanlators)) },
        text = textFunc@{
            if (sortedAvailableScanlators.isEmpty()) {
                Text(text = stringResource(MR.strings.no_scanlators_found))
                return@textFunc
            }
            Box {
                val state = rememberLazyListState()
                LazyColumn(state = state) {
                    sortedAvailableScanlators.forEach { scanlator ->
                        item {
                            val isExcluded = mutableExcludedScanlators.contains(scanlator)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        if (isExcluded) {
                                            mutableExcludedScanlators.remove(scanlator)
                                        } else {
                                            mutableExcludedScanlators.add(scanlator)
                                        }
                                    }
                                    .minimumInteractiveComponentSize()
                                    .clip(MaterialTheme.shapes.small)
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.small),
                            ) {
                                Icon(
                                    imageVector = if (isExcluded) {
                                        Icons.Rounded.DisabledByDefault
                                    } else {
                                        Icons.Rounded.CheckBoxOutlineBlank
                                    },
                                    tint = if (isExcluded) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    },
                                    contentDescription = null,
                                )
                                Text(
                                    text = scanlator,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            }
                        }
                    }
                }
                if (!state.isScrolledToStart()) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                if (!state.isScrolledToEnd()) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            if (sortedAvailableScanlators.isEmpty()) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            } else {
                FlowRow {
                    TextButton(onClick = mutableExcludedScanlators::clear) {
                        Text(text = stringResource(MR.strings.action_reset))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(mutableExcludedScanlators.toSet())
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        },
    )
}
