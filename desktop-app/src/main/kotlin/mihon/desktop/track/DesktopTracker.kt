package mihon.desktop.track

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DesktopTracker {
    val id: Long
    val name: String
    val authType: TrackerAuthType
    val authUrl: String?
    val username: String?
    val serverUrl: String?
    val supportedStatuses: List<TrackStatus> get() = TrackStatus.entries
    val supportsScore: Boolean get() = true

    /** Restorable session credential; OAuth services return the exchanged token here. */
    val persistenceToken: String?
    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>

    suspend fun login(credentials: Map<String, String>): Boolean
    fun restoreLogin(info: TrackerLoginInfo)
    fun logout()

    suspend fun search(query: String): List<TrackSearchResult>
    suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord?
    suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord
}

abstract class BaseDesktopTracker(
    override val id: Long,
    override val name: String,
    override val authType: TrackerAuthType = TrackerAuthType.CREDENTIALS,
    override val authUrl: String? = null,
) : DesktopTracker {
    private val loggedInState = MutableStateFlow(false)
    override val isLoggedIn: Boolean get() = loggedInState.value
    override val isLoggedInFlow: Flow<Boolean> = loggedInState.asStateFlow()

    private val _username = MutableStateFlow<String?>(null)
    override val username: String? get() = _username.value

    private val _serverUrl = MutableStateFlow<String?>(null)
    override val serverUrl: String? get() = _serverUrl.value

    protected var token: String? = null
    override val persistenceToken: String? get() = token

    protected fun setLoggedIn(
        value: Boolean,
        user: String? = null,
        authToken: String? = null,
        url: String? = null,
    ) {
        loggedInState.value = value
        _username.value = user
        token = authToken
        _serverUrl.value = url
    }

    open override fun restoreLogin(info: TrackerLoginInfo) {
        setLoggedIn(true, info.username, info.token, info.serverUrl)
    }

    override fun logout() {
        setLoggedIn(false, null, null, null)
    }
}
