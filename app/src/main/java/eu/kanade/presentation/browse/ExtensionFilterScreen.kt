package eu.kanade.presentation.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.KeyboardArrowDown
import mihon.icons.materialsymbols.rounded.KeyboardArrowUp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun ExtensionFilterScreen(
    navigateUp: () -> Unit,
    state: ExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extensions),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        ExtensionFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLang = onClickToggle,
        )
    }
}

/**
 * One line of the filter list. The rows are flattened because the language switches belong to the
 * store that offers them, and a lazy list can't nest another one.
 */
private sealed interface FilterRow {
    data class Header(val text: String) : FilterRow
    data class Store(val store: ExtensionFilterState.Success.StoreContents) : FilterRow
    data class Language(val code: String) : FilterRow
}

@Composable
private fun ExtensionFilterContent(
    contentPadding: PaddingValues,
    state: ExtensionFilterState.Success,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    // Stores start unfolded, so only the ones the user folded away are recorded
    val foldedStores = remember { mutableStateMapOf<String, Boolean>() }
    val languageHeader = stringResource(MR.strings.extension_filter_languages)

    val rows = buildList {
        add(FilterRow.Header(languageHeader))
        if (state.stores.isEmpty()) {
            // Nothing to unfold, so the languages stand on their own
            state.languages.forEach { add(FilterRow.Language(it)) }
        } else {
            state.stores.forEach { store ->
                add(FilterRow.Store(store))
                if (foldedStores[store.indexUrl] != true) {
                    store.languages.forEach { add(FilterRow.Language(it)) }
                }
            }
        }
    }

    LazyColumn(
        contentPadding = contentPadding,
    ) {
        items(rows) { row ->
            when (row) {
                is FilterRow.Header -> {
                    Text(
                        text = row.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                is FilterRow.Store -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { foldedStores[row.store.indexUrl] = foldedStores[row.store.indexUrl] != true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.store.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Icon(
                            imageVector = if (foldedStores[row.store.indexUrl] != true) {
                                MaterialSymbols.Rounded.KeyboardArrowUp
                            } else {
                                MaterialSymbols.Rounded.KeyboardArrowDown
                            },
                            contentDescription = null,
                        )
                    }
                }
                is FilterRow.Language -> {
                    SwitchPreferenceWidget(
                        title = LocaleHelper.getSourceDisplayName(row.code, context),
                        checked = row.code in state.enabledLanguages,
                        onCheckedChanged = { onClickLang(row.code) },
                    )
                }
            }
        }
    }
}
