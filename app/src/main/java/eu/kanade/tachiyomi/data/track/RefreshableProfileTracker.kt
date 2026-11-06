package eu.kanade.tachiyomi.data.track

/**
 * Tracker that supports displaying a username & refresh user settings from the tracker (e.g. nicknames, scoring
 * systems, etc.).
 */
interface RefreshableProfileTracker {

    suspend fun updateUserConfig()
}
