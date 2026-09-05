package mihon.desktop.track

class TrackingConflictResolver {

    fun detectConflict(
        local: DesktopTrackRecord,
        remote: DesktopTrackRecord,
        mangaTitle: String,
        trackerName: String,
    ): TrackConflict? {
        val chapterDiff = local.lastChapterRead != remote.lastChapterRead
        val statusDiff = local.status != remote.status
        val scoreDiff = local.score != remote.score

        if (!chapterDiff && !statusDiff && !scoreDiff) return null

        return TrackConflict(
            trackerId = local.trackerId,
            trackerName = trackerName,
            mangaId = local.mangaId,
            mangaTitle = mangaTitle,
            localChapterRead = local.lastChapterRead,
            remoteChapterRead = remote.lastChapterRead,
            localStatus = local.status,
            remoteStatus = remote.status,
            localScore = local.score,
            remoteScore = remote.score,
        )
    }

    fun resolve(
        local: DesktopTrackRecord,
        remote: DesktopTrackRecord,
        policy: ConflictResolutionPolicy,
    ): DesktopTrackRecord {
        return when (policy) {
            ConflictResolutionPolicy.LOCAL_WINS -> local.copy(remoteId = remote.remoteId)
            ConflictResolutionPolicy.REMOTE_WINS -> remote.copy(id = local.id, mangaId = local.mangaId)
            ConflictResolutionPolicy.PROMPT -> local
        }
    }
}
