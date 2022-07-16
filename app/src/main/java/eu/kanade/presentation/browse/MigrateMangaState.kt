package eu.kanade.presentation.browse

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.manga.model.Manga

interface MigrateMangaState {
    val isLoading: Boolean
    val items: List<Manga>
    val isEmpty: Boolean
}

fun MigrationMangaState(): MigrateMangaState {
    return MigrateMangaStateImpl()
}

class MigrateMangaStateImpl : MigrateMangaState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var items: List<Manga> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { items.isEmpty() }
}
