package android.content.res

import java.io.File
import java.io.InputStream

class AssetManager(private val directory: File) {
    companion object {
        const val ACCESS_UNKNOWN = 0
        const val ACCESS_RANDOM = 1
        const val ACCESS_STREAMING = 2
        const val ACCESS_BUFFER = 3
    }
    private fun resolve(name: String): File {
        val root = directory.canonicalFile
        val target = File(root, name).canonicalFile
        require(target.toPath().startsWith(root.toPath())) { "Asset path escapes extension assets" }
        return target
    }
    fun open(fileName: String): InputStream = resolve(fileName).inputStream()
    fun open(fileName: String, accessMode: Int): InputStream = open(fileName)
    fun list(path: String): Array<String> = resolve(path).list()?.sortedArray() ?: emptyArray()
}
