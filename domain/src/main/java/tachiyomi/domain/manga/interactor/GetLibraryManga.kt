package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.retry
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getLibraryManga()
    }

    @OptIn(FlowPreview::class)
    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
            .retry(3) { cause ->
                // if we hit the NPE due to library being updated during write
                // retry up to 3x, waiting 500ms between retries
                (cause is NullPointerException).also {
                    if (it) delay(500)
                }
            }
            .debounce(1500) // if another update comes in during retries, ditch the retries
            .catch {
                // emit nothing during failure
                // retries didn't work and we don't want to crash
            }
    }
}
