package mihon.feature.mekuri.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Lock
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.source.local.isLocal

/**
 * Mekuri: choose which shelves and sources live in the vault. Opening it asks for the fingerprint / PIN.
 */
class VaultScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val viewModel = metroViewModel<VaultViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val unlocked by MekuriVault.unlocked.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            if (!MekuriVault.unlocked.value) context.unlockMekuriVault()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = "Vault",
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (!unlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "Hidden shelves and sources stay out of your library, Browse, search, Updates " +
                                "and History until you open the vault.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { scope.launch { context.unlockMekuriVault() } }) {
                            Text("Unlock")
                        }
                    }
                }
                return@Scaffold
            }

            ScrollbarLazyColumn(contentPadding = contentPadding) {
                item(key = "intro") {
                    Text(
                        text = "Switch on anything you want kept private. The vault locks again when you leave the " +
                            "app. Reading from vault sources is never saved to history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                item(key = "shelves-header") { SectionHeader("Shelves") }
                if (state.categories.isEmpty()) {
                    item(key = "no-shelves") { EmptyHint("Create categories in You → Categories to hide them here.") }
                }
                items(state.categories, key = { "c${it.id}" }) { category ->
                    SwitchPreferenceWidget(
                        title = category.name,
                        checked = category.id in state.vault.categories,
                        onCheckedChanged = { MekuriVault.setCategoryInVault(category.id, it) },
                    )
                }
                item(key = "sources-header") { SectionHeader("Sources") }
                items(state.sources, key = { "s${it.id}" }) { source ->
                    SwitchPreferenceWidget(
                        title = source.name,
                        subtitle = if (source.id in state.vault.sources) "Hidden · always incognito" else null,
                        checked = source.id in state.vault.sources,
                        onCheckedChanged = { MekuriVault.setSourceInVault(source.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class VaultViewModel(
    getCategories: GetCategories,
    getEnabledSources: GetEnabledSources,
) : ViewModel() {

    data class State(
        val categories: List<Category> = emptyList(),
        val sources: List<Source> = emptyList(),
        val vault: MekuriVault.Filter = MekuriVault.Filter(),
    )

    val state: StateFlow<State> = combine(
        getCategories.subscribe(),
        getEnabledSources.subscribe(),
        MekuriVault.filter,
    ) { categories, sources, vault ->
        State(
            categories = categories.filterNot(Category::isSystemCategory),
            sources = sources.filterNot { it.isLocal() }.distinctBy { it.id }.sortedBy { it.name.lowercase() },
            vault = vault,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}
