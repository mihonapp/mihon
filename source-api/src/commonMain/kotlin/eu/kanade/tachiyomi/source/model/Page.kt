package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    @Transient var uri: Uri? = null, // Deprecated but can't be deleted due to extensions
) : ProgressListener {

    val number: Int
        get() = index + 1

    @Transient
    private val _statusFlow = MutableStateFlow<State>(State.Queue)

    @Transient
    open val statusFlow: Flow<State> = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow: Flow<Int> = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    sealed interface State {
        data object Queue : State
        data object LoadPage : State
        data object DownloadImage : State
        data object Ready : State
        data object Skip : State
        data class Error(val error: Throwable) : State
    }
}
