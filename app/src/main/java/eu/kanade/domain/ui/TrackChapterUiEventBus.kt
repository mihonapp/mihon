package eu.kanade.domain.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object TrackChapterUiEventBus {

    private val _showAuthDialog = MutableSharedFlow<Long>()
    val showAuthDialog: SharedFlow<Long> = _showAuthDialog

    suspend fun notifyShowAuthDialog(trackerId: Long) {
        _showAuthDialog.emit(trackerId)
    }
}
