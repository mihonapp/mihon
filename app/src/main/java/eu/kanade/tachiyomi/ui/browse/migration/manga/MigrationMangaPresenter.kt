package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.os.Bundle
import eu.kanade.domain.manga.interactor.GetFavoritesBySourceId
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrationMangaPresenter(
    private val sourceId: Long,
    private val getFavoritesBySourceId: GetFavoritesBySourceId = Injekt.get()
) : BasePresenter<MigrationMangaController>() {

    private val _state: MutableStateFlow<MigrateMangaState> = MutableStateFlow(MigrateMangaState.Loading)
    val state: StateFlow<MigrateMangaState> = _state.asStateFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getFavoritesBySourceId
                .subscribe(sourceId)
                .catch { exception ->
                    _state.emit(MigrateMangaState.Error(exception))
                }
                .collectLatest { list ->
                    _state.emit(MigrateMangaState.Success(list))
                }
        }
    }
}

sealed class MigrateMangaState {
    object Loading : MigrateMangaState()
    data class Error(val error: Throwable) : MigrateMangaState()
    data class Success(val list: List<Manga>) : MigrateMangaState()
}
