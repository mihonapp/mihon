package mihon.extension.host

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Avoids java.io's volume-metadata query, which Windows denies inside an AppContainer. */
object ExtensionTemporaryFiles {
    @JvmStatic
    fun createTempFile(prefix: String, suffix: String?): File = createTempFile(prefix, suffix, null)

    @JvmStatic
    fun createTempFile(prefix: String, suffix: String?, directory: File?): File {
        require(prefix.length >= 3) { "Prefix string too short" }
        val tail = suffix ?: ".tmp"
        if (File(tail).name != tail) throw IOException("Invalid temporary file suffix")
        val root = directory ?: File(System.getProperty("java.io.tmpdir"))
        return Files.createTempFile(root.toPath(), File(prefix).name, tail).toFile()
    }
}
