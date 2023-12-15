package eu.kanade.tachiyomi.ui.deeplink

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.model.toDomainManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkScreenModel(
    query: String = "",
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
) : StateScreenModel<DeepLinkScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            val source = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val manga = source?.getManga(query)?.let {
                getMangaFromSManga(it, source.id)
            }

            val chapter = if (source?.getUriType(query) == UriType.Chapter && manga != null) {
                source.getChapter(query)?.let { getChapterFromSChapter(it, manga, source) }
            } else {
                null
            }

            mutableState.update {
                if (manga == null) {
                    State.NoResults
                } else {
                    if (chapter == null) {
                        State.Result(manga)
                    } else {
                        State.Result(manga, chapter.id)
                    }
                }
            }
        }
    }

    private suspend fun getChapterFromSChapter(sChapter: SChapter, manga: Manga, source: Source): Chapter? {
        val localChapter = getChapterByUrlAndMangaId.await(sChapter.url, manga.id)

        return if (localChapter == null) {
            val sourceChapters = source.getChapterList(manga.toSManga())
            val newChapters = syncChaptersWithSource.await(sourceChapters, manga, source, false)
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    private suspend fun getMangaFromSManga(sManga: SManga, sourceId: Long): Manga {
        return getMangaByUrlAndSourceId.await(sManga.url, sourceId)
            ?: networkToLocalManga.await(sManga.toDomainManga(sourceId))
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val manga: Manga, val chapterId: Long? = null) : State
    }
}
