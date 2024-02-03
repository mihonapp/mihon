package tachiyomi.core.common.storage

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile
import java.nio.channels.FileChannel

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

val UniFile.displayablePath: String
    get() = filePath ?: uri.toString()

fun UniFile.openReadOnlyChannel(context: Context): FileChannel {
    return ParcelFileDescriptor.AutoCloseInputStream(context.contentResolver.openFileDescriptor(uri, "r")).channel
}
