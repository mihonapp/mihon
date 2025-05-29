package mihon.domain.manga.local.interactor

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import mihon.domain.manga.local.repository.LocalMangaRepository
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

class GetAllLocalSourceManga(
    private val localMangaRepository: LocalMangaRepository,
) {

    suspend fun await(): List<SManga> {
        return localMangaRepository.getAllSManga()
    }

    fun subscribe(): Flow<List<SManga>> {
        return localMangaRepository.getAllSMangaAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetAllLocalSourceManga.logcat(LogPriority.ERROR, it)
            }
    }
}
