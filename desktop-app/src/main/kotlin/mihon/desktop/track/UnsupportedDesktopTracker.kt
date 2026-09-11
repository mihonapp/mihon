package mihon.desktop.track

/** Trackers still being ported. They fail explicitly until their real clients replace these. */
abstract class UnsupportedDesktopTracker(
    id: Long,
    name: String,
    authType: TrackerAuthType,
    authUrl: String? = null,
) : BaseDesktopTracker(id, name, authType, authUrl) {
    final override suspend fun login(credentials: Map<String, String>): Boolean = false
    final override fun restoreLogin(info: TrackerLoginInfo) = Unit
    final override suspend fun search(query: String): List<TrackSearchResult> = unsupported()
    final override suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord? = unsupported()
    final override suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord = unsupported()
    private fun unsupported(): Nothing = throw TrackerNotSupportedException(name)
}
