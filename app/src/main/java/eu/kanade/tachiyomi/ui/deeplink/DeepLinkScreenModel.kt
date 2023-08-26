package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.tachiyomi.source.online.ResolvableSource
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkScreenModel(
    query: String = "",
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<DeepLinkScreenModel.State>(State.Loading) {

    init {
        coroutineScope.launchIO {
            val manga = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableSource>()
                .filter { it.canResolveUri(query) }
                .firstNotNullOfOrNull { it.getManga(query)?.toDomainManga(it.id) }

            mutableState.update {
                if (manga == null) {
                    State.NoResults
                } else {
                    State.Result(manga)
                }
            }
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val manga: Manga) : State
    }
}
