package eu.kanade.tachiyomi.ui.reader.model

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import okio.Buffer
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.decoder.ImageDecoder
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {
    open lateinit var chapter: ReaderChapter

    private var _backingStream = stream
    private var _backingBitmap: Bitmap? = null

    /**
     * A view of this page's image data represented as an [InputStream] thunk.
     * @see [bitmap]
     */
    var stream: (() -> InputStream)?
        get() = _backingStream ?: _backingBitmap?.let { {
            Buffer().apply {
                it.compress(Bitmap.CompressFormat.PNG, 100, outputStream())
            }.inputStream()
        } }
        set(value) {
            _backingStream = value
            _backingBitmap = null
        }

    /**
     * A view of this page's image data represented as a [Bitmap].
     *
     * This will not include any animation data that may be present in [stream], and setting
     * this property will cause any existing animation data to be lost.
     *
     * @see [stream]
     */
    var bitmap: Bitmap?
        get() = _backingBitmap ?: stream?.invoke()?.use {
            ImageDecoder.newInstance(Buffer().readFrom(it).inputStream())?.run {
                try {
                    decode()
                }
                finally {
                    recycle()
                }
            }
        }
        set(value) {
            _backingStream = null
            _backingBitmap = value
        }

    fun getImageSource(): ImageUtil.ImageSource? =
        // Even if the backing data is already a bitmap, we need to clone it so _backingBitmap isn't consumed.
        _backingBitmap?.let { ImageUtil.ImageSource.FromBitmap(Bitmap.createBitmap(it)) }
            ?: _backingStream?.let { ImageUtil.ImageSource.FromBuffer(Buffer().readFrom(it())) }
}
