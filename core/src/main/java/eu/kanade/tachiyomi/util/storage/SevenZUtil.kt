package eu.kanade.tachiyomi.util.storage

import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import tachiyomi.core.util.system.ImageUtil
import java.io.InputStream

object SevenZUtil {
    fun SevenZFile.getImages(): Sequence<InputStream> {
        return generateSequence { runCatching { getNextEntry() }.getOrNull() }
            .filter { !it.isDirectory && ImageUtil.isImage(it.name) { getInputStream(it) } }
            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
            .map(::getInputStream)
            .map { it.use(InputStream::readBytes).inputStream() } // ByteArrayInputStream
    }
}
