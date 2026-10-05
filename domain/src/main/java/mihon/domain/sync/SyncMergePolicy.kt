package mihon.domain.sync

/**
 * Rules for reconciling two versions of the same library entry.
 *
 * Kept free of database and Android types so the decisions that can *remove* a user's data are
 * testable on their own, rather than only through a full restore.
 *
 * Every rule here must give the same answer whichever of the two devices applies it. A rule that
 * favours "the local side" picks a different side on each device, so each keeps its own value,
 * publishes it, and the two never settle. Ties in particular are common rather than exceptional:
 * entries and chapters restored from a backup carry no decision time at all.
 */
object SyncMergePolicy {

    /**
     * Whether the merged entry should stay in the library.
     *
     * Outside a sync, favourites are only ever added: a restore replays a file that may be months
     * old, so an entry missing from it means nothing and must never empty a library.
     *
     * During a sync both sides are live devices, so the most recent decision wins — including when
     * that decision was to remove the entry. That is what lets a removal reach the other devices.
     * A tie keeps the entry: removing one always records when it happened, so two equal times mean
     * nobody removed anything — typically an entry restored from a backup on one device and merely
     * browsed on the other.
     */
    fun resolveFavorite(
        isSync: Boolean,
        localFavorite: Boolean,
        localModifiedAt: Long?,
        incomingFavorite: Boolean,
        incomingModifiedAt: Long?,
    ): Boolean {
        if (!isSync) return localFavorite || incomingFavorite

        val local = localModifiedAt ?: 0L
        val incoming = incomingModifiedAt ?: 0L
        return when {
            incoming > local -> incomingFavorite
            local > incoming -> localFavorite
            else -> localFavorite || incomingFavorite
        }
    }

    /**
     * One chapter's reading state, as one side of a merge sees it.
     *
     * [decidedAt] moves whenever the read flag, the bookmark or the page changes, so it dates the
     * latest of those decisions rather than each one separately.
     */
    data class ChapterState(
        val read: Boolean,
        val lastPageRead: Long,
        val decidedAt: Long,
        val bookmark: Boolean = false,
    )

    /**
     * Reconciles one chapter's reading state.
     *
     * Two situations pull in opposite directions, and only intent tells them apart:
     *
     * - One device is simply further along. Both sides agree on whether the chapter is read, so
     *   nobody decided anything and the furthest page is the truth. Taking the newer value here
     *   would drag a reader back over pages they actually read.
     * - The reader corrected the state on purpose: marked chapters unread, or moved back from
     *   chapter 13 to chapter 5. Now the most recent decision is the truth, even though it goes
     *   backwards — and a union rule would trap that correction on the device that made it.
     *
     * The read flag separates them, because it only flips when someone decides. Disagreement means
     * a decision was made, so the newer one wins; agreement means nobody did, so progress simply
     * takes the furthest point. A tie goes to the side that read the chapter, so that both devices
     * still settle on the same answer.
     *
     * Outside a sync, state is only ever added: replaying an old backup must not undo reading.
     */
    fun resolveChapterState(isSync: Boolean, local: ChapterState, incoming: ChapterState): ChapterState {
        if (!isSync) {
            return local.copy(
                read = local.read || incoming.read,
                bookmark = local.bookmark || incoming.bookmark,
                lastPageRead = maxOf(local.lastPageRead, incoming.lastPageRead),
                decidedAt = maxOf(local.decidedAt, incoming.decidedAt),
            )
        }

        val reading = when {
            local.read == incoming.read -> local.copy(
                lastPageRead = maxOf(local.lastPageRead, incoming.lastPageRead),
                decidedAt = maxOf(local.decidedAt, incoming.decidedAt),
            )
            incoming.decidedAt > local.decidedAt -> incoming
            local.decidedAt > incoming.decidedAt -> local
            incoming.read -> incoming
            else -> local
        }

        return reading.copy(bookmark = resolveBookmark(local, incoming))
    }

    /**
     * A bookmark follows the newer decision, so that removing one can reach the other devices — the
     * union used before meant a bookmark could be added anywhere but never taken away.
     *
     * [ChapterState.decidedAt] is shared with the reading state, though, so a newer time does not
     * always mean a newer bookmark decision. When the newer side has also read further, its time may
     * be about that reading alone: it could be a device that never saw the bookmark and simply kept
     * reading. The bookmark stays in that case. Losing one the reader deliberately set is worse than
     * having to remove one twice.
     */
    private fun resolveBookmark(local: ChapterState, incoming: ChapterState): Boolean {
        if (local.bookmark == incoming.bookmark) return local.bookmark

        val (newer, older) = when {
            incoming.decidedAt > local.decidedAt -> incoming to local
            local.decidedAt > incoming.decidedAt -> local to incoming
            else -> return true
        }

        val readFurther = if (newer.read != older.read) {
            newer.read
        } else {
            newer.lastPageRead > older.lastPageRead
        }

        return readFurther || newer.bookmark
    }

    /**
     * Whose list of chapters to trust when two devices disagree on which chapters exist.
     */
    enum class ChapterSet {
        /** Neither can be trusted over the other: keep every chapter either side has. */
        Union,

        /** The incoming device refreshed later: its list replaces this device's. */
        Incoming,

        /** This device refreshed later: chapters only the other side still lists are not taken. */
        Local,
    }

    /**
     * Sources remove chapters, and list them again under new addresses when a site reorganises. A
     * refresh removed the old ones on one device, and the copy from the other device put them
     * straight back — after a reorganisation, every chapter then showed twice, on both devices.
     *
     * The device that refreshed the entry later saw the list as it is now, so its list wins. Each
     * side says when its list last changed from the source; the later one must be later by more than
     * [CHAPTER_LIST_MARGIN_MS], since two clocks are being compared. Within the margin, or when the
     * incoming side does not say — a shard written before this existed — nothing is removed and the
     * lists are combined as they always were.
     */
    fun resolveChapterSet(isSync: Boolean, localListAt: Long, incomingListAt: Long): ChapterSet = when {
        !isSync || incomingListAt <= 0 -> ChapterSet.Union
        incomingListAt > localListAt + CHAPTER_LIST_MARGIN_MS -> ChapterSet.Incoming
        localListAt > incomingListAt + CHAPTER_LIST_MARGIN_MS -> ChapterSet.Local
        else -> ChapterSet.Union
    }

    const val CHAPTER_LIST_MARGIN_MS = 10 * 60 * 1000L

    /**
     * Keeps the later of two optional timestamps, treating "never set" as null rather than 0 so the
     * distinction survives a round trip through a backup.
     */
    fun newestTimestamp(first: Long?, second: Long?): Long? =
        maxOf(first ?: 0L, second ?: 0L).takeIf { it > 0L }
}
