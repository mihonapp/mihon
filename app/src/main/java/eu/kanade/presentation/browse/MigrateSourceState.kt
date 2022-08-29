package eu.kanade.presentation.browse

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.Source

interface MigrateSourceState {
    val isLoading: Boolean
    val items: List<Pair<Source, Long>>
    val isEmpty: Boolean
    val sortingMode: SetMigrateSorting.Mode
    val sortingDirection: SetMigrateSorting.Direction
}

fun MigrateSourceState(): MigrateSourceState {
    return MigrateSourceStateImpl()
}

class MigrateSourceStateImpl : MigrateSourceState {
    override var isLoading: Boolean by mutableStateOf(true)
    override var items: List<Pair<Source, Long>> by mutableStateOf(emptyList())
    override val isEmpty: Boolean by derivedStateOf { items.isEmpty() }
    override var sortingMode: SetMigrateSorting.Mode by mutableStateOf(SetMigrateSorting.Mode.ALPHABETICAL)
    override var sortingDirection: SetMigrateSorting.Direction by mutableStateOf(SetMigrateSorting.Direction.ASCENDING)
}
