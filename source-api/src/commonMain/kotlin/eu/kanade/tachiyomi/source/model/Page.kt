package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.jvm.internal.DefaultConstructorMarker

@Serializable
open class Page @JvmOverloads constructor(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    @Transient var uri: Uri? = null, // Deprecated but can't be deleted due to extensions
    var text: String? = null, // Added for Novel support - MUST be last for binary compatibility
) : ProgressListener {

    /**
     * Binary compatibility constructor that matches extensions compiled with 4-param Page.
     * Extensions call the synthetic constructor: Page(index, url, imageUrl, uri, defaults, marker)
     * This provides that exact signature.
     */
    @Suppress("UNUSED_PARAMETER")
    constructor(
        index: Int,
        url: String?,
        imageUrl: String?,
        uri: Uri?,
        defaults: Int,
        marker: DefaultConstructorMarker?,
    ) : this(
        index = index,
        url = if (defaults and 0x1 != 0) "" else url ?: "",
        imageUrl = if (defaults and 0x2 != 0) null else imageUrl,
        uri = if (defaults and 0x4 != 0) null else uri,
        text = null, // Always null for extensions compiled without text support
    )

    val number: Int
        get() = index + 1

    @Transient
    private val _statusFlow = MutableStateFlow<State>(State.Queue)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(value) {
            _statusFlow.value = value
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()
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
        data class Error(val error: Throwable) : State
    }
}
