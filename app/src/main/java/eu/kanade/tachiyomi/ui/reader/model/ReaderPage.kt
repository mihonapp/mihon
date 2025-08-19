package eu.kanade.tachiyomi.ui.reader.model

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import okio.Buffer
import tachiyomi.core.common.util.system.ImageUtil
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {
    open lateinit var chapter: ReaderChapter

    private val _refreshFlow = MutableSharedFlow<State>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val statusFlow = merge(super.statusFlow, _refreshFlow).conflate()

    /**
     * Re-emits the latest value of [statusFlow], notifying listeners that something has changed even though neither
     * the page's status nor progress has been modified.
     *
     * This is most often used when the image changes while the current status is already [Ready][Page.State.Ready].
     */
    fun refreshStatus() {
        _refreshFlow.tryEmit(status)
    }

    private var _backingStream = stream

    /**
     * We don't want to need to hold the bitmap in memory for the entire lifetime of the page,
     * so wrapping it in a thunk allows the bitmap to be generated on the fly when requested.
     */
    private var _backingBitmap: (() -> Bitmap)? = null

    /**
     * A view of this page's image data represented as an [InputStream] thunk.
     *
     * This is not guaranteed to be referentially stable, even when the image has not been altered.
     *
     * @see bitmap
     * @see imageHashCode
     */
    var stream: (() -> InputStream)?
        get() = _backingStream ?: _backingBitmap?.let { {
            Buffer().apply {
                it().compress(Bitmap.CompressFormat.PNG, 100, outputStream())
            }.inputStream()
        } }
        set(value) {
            _backingStream = value
            _backingBitmap = null
        }

    /**
     * A view of this page's image data represented as a [Bitmap] thunk. The returned bitmap is owned by the caller.
     *
     * This is not guaranteed to be referentially stable, even when the image has not been altered.
     *
     * This will not include any animation data that may be present in [stream], and setting
     * this property will cause any existing animation data to be lost.
     *
     * @see stream
     * @see imageHashCode
     */
    var bitmap: (() -> Bitmap)?
        get() = _backingBitmap ?: _backingStream?.let { {
            ImageUtil.decodeBitmapFromInputStreamFn(it)!!
        } }
        set(value) {
            _backingStream = null
            _backingBitmap = value
        }

    /**
     * Returns the hash code of the backing image thunk. This is useful when you need a stable way to know whether two
     * images are the same without actually evaluating them.
     */
    fun imageHashCode() = (_backingStream ?: _backingBitmap).hashCode()

    fun getImageSource(): ImageUtil.ImageSource? =
        _backingBitmap?.let { ImageUtil.ImageSource.FromBitmap(it()) }
            ?: _backingStream?.let { ImageUtil.ImageSource.FromBuffer(Buffer().readFrom(it())) }
}
