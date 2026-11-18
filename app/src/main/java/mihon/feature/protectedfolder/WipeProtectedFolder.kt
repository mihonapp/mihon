package mihon.feature.protectedfolder

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository
import mihon.domain.protectedfolder.service.ProtectedFolderPin
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * The "Forgot PIN?" recovery: deletes everything in the protected folder and then the PIN.
 */
@Inject
class WipeProtectedFolder(
    private val repository: ProtectedMangaRepository,
    private val pin: ProtectedFolderPin,
    private val session: ProtectedFolderSession,
    private val downloadManager: DownloadManager,
    private val coverCache: CoverCache,
    private val sourceManager: SourceManager,
) {

    suspend fun await(): Result {
        val manga = try {
            val manga = repository.getProtectedManga()
            // One statement, so either every entry goes or none does
            repository.deleteProtectedManga()
            manga
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to wipe the protected folder" }
            return Result.Failed
        }

        // Only once the entries are gone: clearing it first would leave them open to whoever sets the next PIN
        pin.clear()
        session.lock()

        // Files can't be rolled back, so they go last, when the entries they belong to no longer exist
        val leftovers = manga.count { !deleteFiles(it) }
        return if (leftovers == 0) Result.Success else Result.FilesLeft(leftovers)
    }

    private suspend fun deleteFiles(manga: Manga): Boolean {
        return try {
            downloadManager.deleteManga(manga, sourceManager.getOrStub(manga.source))
            manga.removeCovers(coverCache)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The title is what the folder hides, so it stays out of the log
            logcat(LogPriority.ERROR, e) { "Failed to delete the files of a wiped entry" }
            false
        }
    }

    sealed interface Result {
        data object Success : Result

        /**
         * Nothing was deleted and the PIN is unchanged.
         */
        data object Failed : Result

        /**
         * The folder was wiped, but the downloads or covers of [count] entries could not be deleted.
         */
        data class FilesLeft(val count: Int) : Result
    }
}
