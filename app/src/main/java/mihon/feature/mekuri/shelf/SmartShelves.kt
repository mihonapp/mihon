package mihon.feature.mekuri.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen

/** Mekuri: shelves that fill themselves from the library (just Downloaded for now). */
enum class SmartShelf(val title: String, val key: Long) {
    Downloaded("Downloaded", -104),
    ;

    /** Picks and orders this shelf's manga from library items. Returns ids, best first. */
    fun select(items: List<LibraryItem>): List<Long> = when (this) {
        Downloaded -> items.filter { it.downloadCount > 0 }.sortedByDescending { it.downloadCount }
    }
        .map { it.id }
        .distinct()
}

/** The grid that opens from a smart shelf. */
class SmartShelfScreen(
    private val shelf: SmartShelf,
    private val mangaIds: List<Long>,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<SmartShelfViewModel>()
        viewModel.setIds(mangaIds)
        val manga by viewModel.manga.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(title = shelf.title, navigateUp = navigator::pop, scrollBehavior = scrollBehavior)
            },
        ) { contentPadding ->
            if (manga.isEmpty()) {
                EmptyScreen(message = "Nothing here right now.", modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(manga, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.clickable { navigator.push(MangaScreen(item.id)) },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MangaCover.Book(data = item, modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class SmartShelfViewModel(
    getLibraryManga: GetLibraryManga,
) : ViewModel() {

    private val ids = MutableStateFlow<List<Long>>(emptyList())

    fun setIds(value: List<Long>) {
        if (ids.value != value) ids.value = value
    }

    val manga: StateFlow<List<Manga>> = combine(getLibraryManga.subscribe(), ids) { library, ids ->
        val byId = library.associateBy({ it.manga.id }, { it.manga })
        ids.mapNotNull { byId[it] }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
