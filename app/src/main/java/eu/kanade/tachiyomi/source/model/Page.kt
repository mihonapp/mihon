package eu.kanade.tachiyomi.source.model

import android.net.Uri
import eu.kanade.tachiyomi.network.ProgressListener
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import rx.subjects.Subject
import tachiyomi.source.model.PageUrl

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
    var status: Int = 0
        set(value) {
            field = value
            statusSubject?.onNext(value)
            statusCallback?.invoke(this)
        }

    @Transient
    @Volatile
    var progress: Int = 0
        set(value) {
            field = value
            statusCallback?.invoke(this)
        }

    @Transient
    private var statusSubject: Subject<Int, Int>? = null

    @Transient
    private var statusCallback: ((Page) -> Unit)? = null

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    fun setStatusSubject(subject: Subject<Int, Int>?) {
        this.statusSubject = subject
    }

    fun setStatusCallback(f: ((Page) -> Unit)?) {
        statusCallback = f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Page) return false

        if (index != other.index) return false
        if (url != other.url) return false
        if (imageUrl != other.imageUrl) return false
        if (number != other.number) return false
        if (status != other.status) return false
        if (progress != other.progress) return false
        if (statusSubject != other.statusSubject) return false
        if (statusCallback != other.statusCallback) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + url.hashCode()
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + status
        result = 31 * result + progress
        result = 31 * result + (statusSubject?.hashCode() ?: 0)
        result = 31 * result + (statusCallback?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val QUEUE = 0
        const val LOAD_PAGE = 1
        const val DOWNLOAD_IMAGE = 2
        const val READY = 3
        const val ERROR = 4
    }
}

fun Page.toPageUrl(): PageUrl {
    return PageUrl(
        url = this.imageUrl ?: this.url,
    )
}

fun PageUrl.toPage(index: Int): Page {
    return Page(
        index = index,
        imageUrl = this.url,
    )
}
