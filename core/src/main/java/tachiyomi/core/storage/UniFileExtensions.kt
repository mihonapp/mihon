package tachiyomi.core.storage

import android.content.Context
import android.os.Build
import android.os.FileUtils
import com.hippo.unifile.UniFile
import java.io.BufferedOutputStream
import java.io.File

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

fun UniFile.toTempFile(context: Context): File {
    val inputStream = context.contentResolver.openInputStream(uri)!!
    val tempFile = File.createTempFile(
        nameWithoutExtension.orEmpty().padEnd(3), // Prefix must be 3+ chars
        null,
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        FileUtils.copy(inputStream, tempFile.outputStream())
    } else {
        BufferedOutputStream(tempFile.outputStream()).use { tmpOut ->
            inputStream.use { input ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } > 0) {
                    tmpOut.write(buffer, 0, count)
                }
            }
        }
    }

    return tempFile
}
