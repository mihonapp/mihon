package eu.kanade.tachiyomi.ui.manga.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.EditMangaInfoScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EditMangaInfoScreen(
    private val manga: Manga,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model(manga) }
        val state by screenModel.state.collectAsState()

        EditMangaInfoScreen(
            state = state,
            navigateUp = navigator::pop,
            onUpdate = screenModel::updateMangaCustomInfo,
        )
    }

    private class Model(
        private val manga: Manga,
        private val updateManga: UpdateManga = Injekt.get(),
    ) : StateScreenModel<State>(State(manga)) {
        fun updateMangaCustomInfo(manga: Manga) {
            val newTitle = if (manga.customTitle.isNullOrBlank()) null else manga.customTitle
            val newAuthor = if (manga.customAuthor.isNullOrBlank()) null else manga.customAuthor
            val newArtist = if (manga.customArtist.isNullOrBlank()) null else manga.customArtist
            val newDescription = if (manga.customDescription.isNullOrBlank()) null else manga.customDescription
            val newThumbnailUrl = if (manga.customThumbnailUrl.isNullOrBlank()) null else manga.customThumbnailUrl

            screenModelScope.launchNonCancellable {
                updateManga.awaitUpdateEditInfo(
                    MangaUpdate(
                        id = manga.id,
                        title = newTitle,
                        artist = newArtist,
                        author = newAuthor,
                        description = newDescription,
                        thumbnailUrl = newThumbnailUrl,
                        status = manga.status,
                        genre = manga.genre,
                    ),
                )
            }
        }
    }

    @Immutable
    data class State(
        val manga: Manga,
    )
}
