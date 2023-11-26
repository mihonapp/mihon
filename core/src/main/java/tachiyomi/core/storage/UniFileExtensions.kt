package tachiyomi.core.storage

import com.hippo.unifile.UniFile
import java.io.File

val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

fun UniFile.toFile(): File? = filePath?.let { File(it) }
