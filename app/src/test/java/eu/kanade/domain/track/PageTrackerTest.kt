package eu.kanade.domain.track

import eu.kanade.domain.track.interactor.SyncChapterProgressWithTrack
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.track.PageTracker
import org.junit.jupiter.api.Test


class PageTrackerTest {
    companion object {
        private object SampleSeries {
            val chaptersWithRemoteProgress: Map<Chapter, PageTracker.ChapterReadProgress> = mapOf(
                createTestChapterEntry(1, true, 100, false, 5),  //reread finished
                createTestChapterEntry(2, true, 100, true, 100),
                createTestChapterEntry(3, false, 3, false, 3),
                createTestChapterEntry(4, false, 5, false, 10),
                createTestChapterEntry(5, false, 10, false, 15),
                createTestChapterEntry(6, false, 10, false, 5),
                createTestChapterEntry(7, false, 0, false, 0),
                createTestChapterEntry(8, true, 100, false, 0), //local read, remote has not started reread
                createTestChapterEntry(9, false, 0, false, -1),
                createTestChapterEntry(10, true, 3, false, 5), //local read, but has reread history; remote reread
            )
        }
        
        private val Chapter.progress: PageTracker.ChapterReadProgress
            get() = PageTracker.ChapterReadProgress(read, last_page_read)

        private fun PageTracker.ChapterReadProgress.compareWith(b: PageTracker.ChapterReadProgress): String {
            return StringBuilder("Update(").apply {
                if (completed != b.completed) append("completed: ${b.completed} -> $completed; ")
                if (page != b.page) append("page: ${b.page} ->  $page")
                append(")")
            }.toString()
        }

        private fun createTestChapterEntry(localId: Int,  localRead: Boolean, localPage: Int, remoteRead: Boolean, remotePage: Int) =
            ChapterImpl().apply {
                id = localId.toLong()
                read = localRead
                last_page_read = localPage
                name = "Chapter $localId"
                url = "sample.site/series/114514/books/$localId"
            } to PageTracker.ChapterReadProgress(remoteRead, remotePage)
    }

    @Test
    fun testSyncStrategies() {
        testSampleWithStrategy(1)
        testSampleWithStrategy(2)
        testSampleWithStrategy(3)
    }

    private fun testSampleWithStrategy(strategy: Int) {
        SyncChapterProgressWithTrack.Companion.syncStrategy = strategy
        val result = SampleSeries.chaptersWithRemoteProgress.entries.groupBy {
            SyncChapterProgressWithTrack.Companion.resolveRemoteProgress(it.key, it.value)
        }

        println("\nStrategy: $strategy, split: ${result.entries.associate { it.key to it.value.size }}")


        println("Update to local : ${result[SyncChapterProgressWithTrack.RemoteProgressResolution.ACCEPT]?.map { "${it.key.name}  ${it.value.compareWith(it.key.progress)}" }}")
        println("Update to remote : ${result[SyncChapterProgressWithTrack.RemoteProgressResolution.REJECT]?.map {  "${it.key.name}  ${it.key.progress.compareWith(it.value)}"  }}")
        println("No change : ${result[SyncChapterProgressWithTrack.RemoteProgressResolution.SAME]?.map { it.key.name }}")
    }


}
