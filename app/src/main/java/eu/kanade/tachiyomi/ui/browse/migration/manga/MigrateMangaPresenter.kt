package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.os.Bundle
import eu.kanade.domain.manga.interactor.GetFavorites
import eu.kanade.presentation.browse.MigrateMangaState
import eu.kanade.presentation.browse.MigrateMangaStateImpl
import eu.kanade.presentation.browse.MigrationMangaState
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateMangaPresenter(
    private val sourceId: Long,
    private val state: MigrateMangaStateImpl = MigrationMangaState() as MigrateMangaStateImpl,
    private val getFavorites: GetFavorites = Injekt.get(),
) : BasePresenter<MigrationMangaController>(), MigrateMangaState by state {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        presenterScope.launchIO {
            getFavorites
                .subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingFavorites)
                }
                .map { list ->
                    list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
                .collectLatest { sortedList ->
                    state.isLoading = false
                    state.items = sortedList
                }
        }
    }

    sealed class Event {
        object FailedFetchingFavorites : Event()
    }
}
