package eu.kanade.tachiyomi.data.download

import android.content.ContentResolver
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import java.io.File
import java.io.IOException

internal fun UniFile.requireReadableDirectory() {
    if (!exists() || !isDirectory || !canRead()) {
        throw IOException("Download storage directory is unavailable")
    }
}

internal fun UniFile.findReadableDirectory(name: String): UniFile? {
    val child = findFile(name)
    // A missing child is meaningful only while its parent remains readable.
    requireReadableDirectory()
    child?.requireReadableDirectory()
    return child
}

internal fun UniFile.directoryIdentity() = uri.let {
    if (it.scheme == ContentResolver.SCHEME_FILE) {
        File(it.path ?: throw IOException("Download storage path is unavailable")).canonicalFile.toUri()
    } else {
        it
    }
}
