package mihon.domain.sync

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import mihon.domain.sync.SyncMergePolicy.ChapterSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SyncMergePolicyTest {

    // Restores must never remove anything: the file being replayed can be arbitrarily old, so a
    // missing favourite carries no information.

    @Test
    fun `restore keeps a local favourite the backup does not have`() {
        resolve(isSync = false, local = true, localAt = 100, incoming = false, incomingAt = 900) shouldBe true
    }

    @Test
    fun `restore adds a favourite the backup has`() {
        resolve(isSync = false, local = false, localAt = 900, incoming = true, incomingAt = 100) shouldBe true
    }

    @Test
    fun `restore leaves a non-favourite alone`() {
        resolve(isSync = false, local = false, localAt = 0, incoming = false, incomingAt = 0) shouldBe false
    }

    // Syncs reconcile two live devices, so the most recent decision wins in both directions.

    @Test
    fun `sync propagates a removal made more recently elsewhere`() {
        resolve(isSync = true, local = true, localAt = 100, incoming = false, incomingAt = 900) shouldBe false
    }

    @Test
    fun `sync keeps a local addition newer than a remote removal`() {
        resolve(isSync = true, local = true, localAt = 900, incoming = false, incomingAt = 100) shouldBe true
    }

    @Test
    fun `sync propagates an addition made more recently elsewhere`() {
        resolve(isSync = true, local = false, localAt = 100, incoming = true, incomingAt = 900) shouldBe true
    }

    @Test
    fun `sync keeps a local removal newer than a remote addition`() {
        resolve(isSync = true, local = false, localAt = 900, incoming = true, incomingAt = 100) shouldBe false
    }

    @Test
    fun `a tie keeps the entry, whichever device merges`() {
        resolve(isSync = true, local = true, localAt = 500, incoming = false, incomingAt = 500) shouldBe true
        resolve(isSync = true, local = false, localAt = 500, incoming = true, incomingAt = 500) shouldBe true
    }

    @Test
    fun `sync treats a never-toggled entry as the oldest possible state`() {
        // A remote entry that was explicitly unfavourited beats a local one that was never touched.
        resolve(isSync = true, local = true, localAt = null, incoming = false, incomingAt = 1) shouldBe false
        // With neither side timestamped nobody removed anything, so the entry stays.
        resolve(isSync = true, local = true, localAt = null, incoming = false, incomingAt = null) shouldBe true
    }

    @Test
    fun `an entry restored on one device and only browsed on the other joins both libraries`() {
        // Restores carry no favourite timestamp, and neither does an entry merely opened from Browse.
        resolve(isSync = true, local = false, localAt = null, incoming = true, incomingAt = null) shouldBe true
        resolve(isSync = true, local = true, localAt = null, incoming = false, incomingAt = null) shouldBe true
    }

    @Test
    fun `favourites resolve the same whichever device merges`() {
        val timestamps = listOf(null, 0L, 100L, 900L)
        for (localFavorite in listOf(true, false)) {
            for (incomingFavorite in listOf(true, false)) {
                for (localAt in timestamps) {
                    for (incomingAt in timestamps) {
                        withClue("$localFavorite@$localAt vs $incomingFavorite@$incomingAt") {
                            resolve(true, localFavorite, localAt, incomingFavorite, incomingAt) shouldBe
                                resolve(true, incomingFavorite, incomingAt, localFavorite, localAt)
                        }
                    }
                }
            }
        }
    }

    // Reading progress. Two opposite hazards: dragging a reader backwards over pages they read,
    // and trapping a deliberate correction on the device that made it.

    private fun state(read: Boolean, page: Long, at: Long, bookmark: Boolean = false) =
        SyncMergePolicy.ChapterState(read = read, lastPageRead = page, decidedAt = at, bookmark = bookmark)

    @Test
    fun `progress never moves backwards when the incoming device is merely behind`() {
        val merged = SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = false, page = 150, at = 100),
            incoming = state(read = false, page = 20, at = 900),
        )
        merged.lastPageRead shouldBe 150
        merged.read shouldBe false
    }

    @Test
    fun `progress moves forward when the incoming device is ahead`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = false, page = 20, at = 900),
            incoming = state(read = false, page = 150, at = 100),
        ).lastPageRead shouldBe 150
    }

    @Test
    fun `a newer decision to mark unread propagates`() {
        val merged = SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = true, page = 150, at = 100),
            incoming = state(read = false, page = 0, at = 900),
        )
        merged.read shouldBe false
        merged.lastPageRead shouldBe 0
    }

    @Test
    fun `a newer decision to mark read propagates`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = false, page = 10, at = 100),
            incoming = state(read = true, page = 0, at = 900),
        ).read shouldBe true
    }

    @Test
    fun `an older decision to mark unread is refused`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = true, page = 150, at = 900),
            incoming = state(read = false, page = 0, at = 100),
        ).read shouldBe true
    }

    @Test
    fun `a tie on the decision goes to the read side, whichever device merges`() {
        // Chapters restored from a backup all share the same, empty, decision time.
        val read = state(read = true, page = 150, at = 0)
        val unread = state(read = false, page = 0, at = 0)

        SyncMergePolicy.resolveChapterState(isSync = true, local = read, incoming = unread) shouldBe read
        SyncMergePolicy.resolveChapterState(isSync = true, local = unread, incoming = read) shouldBe read
    }

    @Test
    fun `restore only ever adds reading state`() {
        // Replaying a months-old backup must not undo reading, whatever its timestamps claim.
        val merged = SyncMergePolicy.resolveChapterState(
            isSync = false,
            local = state(read = true, page = 150, at = 100),
            incoming = state(read = false, page = 0, at = 900),
        )
        merged.read shouldBe true
        merged.lastPageRead shouldBe 150
    }

    // Bookmarks. A newer decision must be able to remove one, but a device that merely kept reading
    // must not take away a bookmark it never saw.

    @Test
    fun `a newer bookmark removal propagates`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = true, page = 30, at = 100, bookmark = true),
            incoming = state(read = true, page = 30, at = 900, bookmark = false),
        ).bookmark shouldBe false
    }

    @Test
    fun `an older bookmark removal is refused`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = true, page = 30, at = 900, bookmark = true),
            incoming = state(read = true, page = 30, at = 100, bookmark = false),
        ).bookmark shouldBe true
    }

    @Test
    fun `a newer bookmark propagates`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = false, page = 10, at = 100),
            incoming = state(read = false, page = 10, at = 900, bookmark = true),
        ).bookmark shouldBe true
    }

    @Test
    fun `a bookmark survives a device that only read further`() {
        val bookmarked = state(read = false, page = 10, at = 100, bookmark = true)

        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = bookmarked,
            incoming = state(read = false, page = 30, at = 900),
        ).bookmark shouldBe true

        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = bookmarked,
            incoming = state(read = true, page = 40, at = 900),
        ).bookmark shouldBe true
    }

    @Test
    fun `a bookmark removal reaches a device that is behind`() {
        SyncMergePolicy.resolveChapterState(
            isSync = true,
            local = state(read = false, page = 30, at = 100, bookmark = true),
            incoming = state(read = false, page = 10, at = 900, bookmark = false),
        ).bookmark shouldBe false
    }

    @Test
    fun `a tie on a bookmark keeps it`() {
        val with = state(read = true, page = 30, at = 500, bookmark = true)
        val without = state(read = true, page = 30, at = 500, bookmark = false)

        SyncMergePolicy.resolveChapterState(isSync = true, local = with, incoming = without).bookmark shouldBe true
        SyncMergePolicy.resolveChapterState(isSync = true, local = without, incoming = with).bookmark shouldBe true
    }

    @Test
    fun `restore only ever adds bookmarks`() {
        SyncMergePolicy.resolveChapterState(
            isSync = false,
            local = state(read = true, page = 30, at = 100, bookmark = true),
            incoming = state(read = true, page = 30, at = 900, bookmark = false),
        ).bookmark shouldBe true
    }

    @Test
    fun `chapters resolve the same whichever device merges`() {
        // A rule that leans on the local side picks a different answer on each device, and the two
        // then republish their own for ever. Every combination must give a single answer.
        val states = buildList {
            for (read in listOf(true, false)) {
                for (bookmark in listOf(true, false)) {
                    for (page in listOf(0L, 10L, 30L)) {
                        for (at in listOf(0L, 100L, 900L)) {
                            add(state(read = read, page = page, at = at, bookmark = bookmark))
                        }
                    }
                }
            }
        }

        for (a in states) {
            for (b in states) {
                withClue("$a vs $b") {
                    SyncMergePolicy.resolveChapterState(isSync = true, local = a, incoming = b) shouldBe
                        SyncMergePolicy.resolveChapterState(isSync = true, local = b, incoming = a)
                }
            }
        }
    }

    // Which chapters exist. The device that refreshed later saw the source as it is now.

    private val margin = SyncMergePolicy.CHAPTER_LIST_MARGIN_MS

    private fun chapterSet(localListAt: Long, incomingListAt: Long, isSync: Boolean = true) =
        SyncMergePolicy.resolveChapterSet(isSync, localListAt, incomingListAt)

    @Test
    fun `the list of the device that refreshed later wins`() {
        chapterSet(localListAt = 1_000, incomingListAt = 1_000 + margin + 1) shouldBe ChapterSet.Incoming
        chapterSet(localListAt = 1_000 + margin + 1, incomingListAt = 1_000) shouldBe ChapterSet.Local
    }

    @Test
    fun `a device that never refreshed the entry takes the other one's list`() {
        chapterSet(localListAt = 0, incomingListAt = 1_000 + margin) shouldBe ChapterSet.Incoming
    }

    @Test
    fun `refreshes too close to tell apart remove nothing`() {
        chapterSet(localListAt = 1_000, incomingListAt = 1_000 + margin) shouldBe ChapterSet.Union
        chapterSet(localListAt = 1_000 + margin, incomingListAt = 1_000) shouldBe ChapterSet.Union
    }

    @Test
    fun `a shard that does not say when its list changed removes nothing`() {
        chapterSet(localListAt = 1_000, incomingListAt = 0) shouldBe ChapterSet.Union
    }

    @Test
    fun `a restore never removes chapters`() {
        chapterSet(localListAt = 0, incomingListAt = 1_000 + margin, isSync = false) shouldBe ChapterSet.Union
    }

    @Test
    fun `both devices agree on whose list wins`() {
        val times = listOf(1_000L, 1_000L + margin, 1_000L + 2 * margin + 1, 10 * margin)
        for (local in times) {
            for (incoming in times) {
                val mirrored = when (chapterSet(local, incoming)) {
                    ChapterSet.Incoming -> ChapterSet.Local
                    ChapterSet.Local -> ChapterSet.Incoming
                    ChapterSet.Union -> ChapterSet.Union
                }
                withClue("$local vs $incoming") {
                    chapterSet(incoming, local) shouldBe mirrored
                }
            }
        }
    }

    @Test
    fun `newestTimestamp keeps the later value and maps never-set to null`() {
        SyncMergePolicy.newestTimestamp(100, 900) shouldBe 900
        SyncMergePolicy.newestTimestamp(900, 100) shouldBe 900
        SyncMergePolicy.newestTimestamp(null, 100) shouldBe 100
        SyncMergePolicy.newestTimestamp(100, null) shouldBe 100
        SyncMergePolicy.newestTimestamp(null, null) shouldBe null
        SyncMergePolicy.newestTimestamp(0, 0) shouldBe null
    }

    private fun resolve(
        isSync: Boolean,
        local: Boolean,
        localAt: Long?,
        incoming: Boolean,
        incomingAt: Long?,
    ) = SyncMergePolicy.resolveFavorite(
        isSync = isSync,
        localFavorite = local,
        localModifiedAt = localAt,
        incomingFavorite = incoming,
        incomingModifiedAt = incomingAt,
    )
}
