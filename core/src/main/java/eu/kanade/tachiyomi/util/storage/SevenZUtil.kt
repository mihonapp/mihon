package eu.kanade.tachiyomi.util.storage

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import org.apache.commons.compress.archivers.dump.UnsupportedCompressionAlgorithmException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import tachiyomi.core.util.system.ImageUtil
import java.io.IOException
import java.io.InputStream

object SevenZUtil {
    private val GoodMethods = arrayOf(
        SevenZMethod.LZMA2,
        SevenZMethod.DEFLATE,
        SevenZMethod.COPY,
    )

    fun SevenZFile.getImages(notifySlowArchives: (method: String) -> Unit): Sequence<ByteArray> {
        return generateSequence {
            try {
                getNextEntry()
            } catch (e: IOException) {
                throw UnsupportedCompressionAlgorithmException()
            }
        }.filter { !it.isDirectory && ImageUtil.isImage(it.name) { getInputStream(it) } }
            .onEachIndexed { i, it ->
                if (i > 0) return@onEachIndexed
                val method = it.contentMethods.first().method
                if(method !in GoodMethods) notifySlowArchives(method.name)
            }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .map(::getInputStream)
            .map { it.use(InputStream::readBytes) } // ByteArray
    }
}
