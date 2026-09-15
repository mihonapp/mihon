package mihon.desktop.extension

import mihon.extension.model.ExtensionManifest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.objectweb.asm.ClassReader
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Older converted Android packages have no domain metadata. Recover exact URL hosts from their code. */
internal object ExtensionPackageDomains {
    fun resolve(file: File, manifest: ExtensionManifest): List<String> {
        if (manifest.declaredDomains.isNotEmpty() ||
            !manifest.id.startsWith("eu.kanade.tachiyomi.extension.")
        ) {
            return manifest.declaredDomains
        }
        val domains = linkedSetOf<String>()
        fun inspect(bytes: ByteArray) {
            val reader = ClassReader(bytes)
            val chars = CharArray(reader.maxStringLength)
            for (index in 1 until reader.itemCount) {
                val offset = reader.getItem(index)
                if (offset == 0 || reader.readByte(offset - 1) != 8) continue
                val literal = reader.readConst(index, chars) as? String ?: continue
                URL.findAll(literal).forEach { match ->
                    match.value.toHttpUrlOrNull()?.host?.let(domains::add)
                }
            }
        }
        ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                when {
                    entry.name.endsWith(".class") -> inspect(zip.getInputStream(entry).use { it.readBytes() })
                    entry.name.endsWith(".jar") -> ZipInputStream(zip.getInputStream(entry)).use { jar ->
                        while (true) {
                            val nested = jar.nextEntry ?: break
                            if (!nested.isDirectory && nested.name.endsWith(".class")) inspect(jar.readBytes())
                        }
                    }
                }
            }
        }
        return domains.toList()
    }

    private val URL = Regex("https?://[a-zA-Z0-9.-]+(?::[0-9]+)?")
}
