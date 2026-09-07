package mihon.desktop.track

import mihon.desktop.preferences.DesktopPreferenceStore

class DesktopTrackerStore(private val preferences: DesktopPreferenceStore) {

    fun isLoggedIn(trackerId: Long): Boolean {
        return preferences.property(key(trackerId, "logged_in"))?.toBooleanStrictOrNull() ?: false
    }

    fun getUsername(trackerId: Long): String? {
        return preferences.property(key(trackerId, "username"))
    }

    fun getAuthToken(trackerId: Long): String? {
        return preferences.property(key(trackerId, "token"))
    }

    fun getServerUrl(trackerId: Long): String? {
        return preferences.property(key(trackerId, "server_url"))
    }

    fun getLoginInfo(trackerId: Long): TrackerLoginInfo? {
        if (!isLoggedIn(trackerId)) return null
        return TrackerLoginInfo(
            trackerId = trackerId,
            username = getUsername(trackerId).orEmpty(),
            token = getAuthToken(trackerId).orEmpty(),
            serverUrl = getServerUrl(trackerId).orEmpty(),
        )
    }

    fun saveLogin(trackerId: Long, username: String, token: String, serverUrl: String = "") {
        preferences.update {
            setProperty(key(trackerId, "logged_in"), "true")
            setProperty(key(trackerId, "username"), username)
            setProperty(key(trackerId, "token"), token)
            if (serverUrl.isNotBlank()) {
                setProperty(key(trackerId, "server_url"), serverUrl)
            } else {
                remove(key(trackerId, "server_url"))
            }
        }
    }

    fun clearLogin(trackerId: Long) {
        preferences.update {
            remove(key(trackerId, "logged_in"))
            remove(key(trackerId, "username"))
            remove(key(trackerId, "token"))
            remove(key(trackerId, "server_url"))
        }
    }

    private fun key(trackerId: Long, property: String): String = "tracker.$trackerId.$property"
}
