package eu.kanade.tachiyomi.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

class SyncStatus {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)

    val isRunning = _isRunning
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    suspend fun start() {
        _isRunning.emit(true)
    }

    suspend fun stop() {
        _isRunning.emit(false)
    }
}
