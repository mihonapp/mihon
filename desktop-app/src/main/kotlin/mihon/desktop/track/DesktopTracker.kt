package mihon.desktop.track

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface DesktopTracker {
    val id: Long
    val name: String
    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>

    suspend fun login(credentials: Map<String, String>): Boolean
    fun logout()

    suspend fun search(query: String): List<TrackSearchResult>
    suspend fun findRemote(track: DesktopTrackRecord): DesktopTrackRecord?
    suspend fun updateRemote(track: DesktopTrackRecord): DesktopTrackRecord
}

abstract class BaseDesktopTracker(
    override val id: Long,
    override val name: String,
) : DesktopTracker {
    private val loggedInState = MutableStateFlow(false)
    override val isLoggedIn: Boolean get() = loggedInState.value
    override val isLoggedInFlow: Flow<Boolean> = loggedInState.asStateFlow()

    protected fun setLoggedIn(value: Boolean) {
        loggedInState.value = value
    }

    override fun logout() {
        loggedInState.value = false
    }
}
