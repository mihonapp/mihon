package eu.kanade.domain.track.interactor

import android.app.Application
import com.google.common.annotations.VisibleForTesting
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.PageTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.toChapterUpdate
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    companion object {
        //Equal compare
        private const val SYNC_STRATEGY_DEFAULT = 1
        private fun syncStrategyDefault(local: PageTracker.ChapterReadProgress, remote: PageTracker.ChapterReadProgress): RemoteProgressResolution {
            return when {
                local > remote -> RemoteProgressResolution.REJECT
                local < remote -> RemoteProgressResolution.ACCEPT
                else -> RemoteProgressResolution.SAME
            }
        }

        //Flush local with remote
        private const val SYNC_STRATEGY_ACCEPT_ALL = 2
        private fun syncStrategyAcceptAll(local: PageTracker.ChapterReadProgress, remote: PageTracker.ChapterReadProgress): RemoteProgressResolution {
            return if (local.completed && remote.completed || local.page == remote.page) RemoteProgressResolution.SAME else RemoteProgressResolution.ACCEPT
        }

        //Update remote only when both local and remote are not completed and local page index gt remote
        private const val SYNC_STRATEGY_ALLOW_REREAD = 3

        private fun syncStrategyAllowReread(local: PageTracker.ChapterReadProgress, remote: PageTracker.ChapterReadProgress): RemoteProgressResolution {
            return if (local.completed && !remote.completed && remote.page > 1) RemoteProgressResolution.ACCEPT else syncStrategyDefault(local, remote)
        }

        @VisibleForTesting
        internal var syncStrategy = SYNC_STRATEGY_ALLOW_REREAD

        @VisibleForTesting
        internal fun resolveRemoteProgress(chapter: eu.kanade.tachiyomi.data.database.models.Chapter, remote: PageTracker.ChapterReadProgress): RemoteProgressResolution {
            val local = PageTracker.ChapterReadProgress(chapter.read, chapter.last_page_read)
            return when(syncStrategy) {
                SYNC_STRATEGY_ACCEPT_ALL -> syncStrategyAcceptAll(local, remote)
                SYNC_STRATEGY_ALLOW_REREAD -> syncStrategyAllowReread(local, remote)
                else -> syncStrategyDefault(local, remote)
            }
        }

        @VisibleForTesting
        internal val Chapter.debugString:String
            get() = "$name(id = $id, read = $read, page = $last_page_read, url = $url)"
    }

    @VisibleForTesting
    internal enum class RemoteProgressResolution {
        ACCEPT,
        REJECT,
        SAME
    }

    private val trackPreferences: TrackPreferences by injectLazy()

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        if (tracker !is EnhancedTracker) {
            return
        }

        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toChapterUpdate() }
        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val updatedTrack = remoteTrack.copy(lastChapterRead = localLastRead.toDouble())

        try {
            if (tracker is PageTracker && trackPreferences.chapterBasedTracking().get()) {
                val remoteUpdatesMapping = sortedChapters.map { it.toDbChapter() }
                    .let { tracker.batchGetChapterProgress(it) }
                    .entries.groupBy { resolveRemoteProgress(it.key, it.value) }
                val updatesToLocal = remoteUpdatesMapping[RemoteProgressResolution.ACCEPT]?.mapNotNull {  (chapter, remote) ->
                    if (remote.page > 1 && chapter.last_page_read != remote.page - 1 )
                        //In komga page starts from 1
                        chapter.toDomainChapter()?.copy(lastPageRead = remote.page.toLong() - 1, read = remote.completed)?.toChapterUpdate()
                    else null
                } ?: listOf()
                val updatesToRemote = remoteUpdatesMapping[RemoteProgressResolution.REJECT]?.map { it.key } ?: listOf()

                updateChapter.awaitAll(updatesToLocal)
                (tracker as PageTracker).batchUpdateRemoteProgress(updatesToRemote)
                logcat(LogPriority.INFO) {
                    "Tracker $tracker updated page progress" +
                    "\nwrite-local: " + updatesToLocal +
                    "\nwrite-remote " + updatesToRemote.map { it.debugString }
                }
                if (BuildConfig.APPLICATION_ID == "app.mihon.debug") {
                    Injekt.get<Application>().toast("Finished syncing PageTracker ${tracker.javaClass.simpleName}")
                }
            } else {
                tracker.update(updatedTrack.toDbTrack())
                updateChapter.awaitAll(chapterUpdates)
            }
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
