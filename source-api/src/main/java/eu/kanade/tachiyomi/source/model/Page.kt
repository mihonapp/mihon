package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import rx.subjects.Subject

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
    @Volatile
    var status: State = State.QUEUE
        set(value) {
            field = value
            statusSubject?.onNext(value)
        }

    @Transient
    @Volatile
    var progress: Int = 0

    @Transient
    var statusSubject: Subject<State, State>? = null

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    enum class State {
        QUEUE,
        LOAD_PAGE,
        DOWNLOAD_IMAGE,
        READY,
        ERROR,
    }
}
