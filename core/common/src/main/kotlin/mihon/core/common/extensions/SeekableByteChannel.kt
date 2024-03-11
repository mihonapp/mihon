package mihon.core.common.extensions

import org.apache.commons.compress.archivers.zip.ZipFile
import java.nio.channels.SeekableByteChannel

fun SeekableByteChannel.toZipFile(): ZipFile {
    return ZipFile.Builder().setSeekableByteChannel(this).get()
}
