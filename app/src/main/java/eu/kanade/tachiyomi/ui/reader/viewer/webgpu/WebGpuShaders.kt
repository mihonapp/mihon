package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ca.mpreg.webgpuviewer.renderer.ImageFilterWgsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.Preference
import java.io.File
import java.util.UUID

/** A user's shader: [file] in [WebGpuShaders.dir], shown as [name]. */
data class WebGpuShader(val file: String, val name: String)

/** Picked shaders are copied in, so they outlive the picker's URI permission. */
object WebGpuShaders {
    private const val MAX_BYTES = 1 shl 20

    /** What [import] names files; anything else in a preference is not ours to read or delete. */
    private val FILE = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.wgsl""")

    /** Held while files and the preferences naming them change together. */
    private val lock = Any()

    private fun dir(context: Context) = File(context.filesDir, "webgpu_shaders")

    fun serialize(shaders: List<WebGpuShader>): String =
        shaders.joinToString("\n") { "${it.file}\t${it.name}" }

    fun deserialize(text: String): List<WebGpuShader> = text.lineSequence()
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val file = line.substring(0, tab)
            if (FILE.matches(file)) WebGpuShader(file, line.substring(tab + 1)) else null
        }
        .distinctBy { it.file }
        .toList()

    /**
     * Copies [uri] in and appends it to [pref]. Throws [IllegalArgumentException], with the reason, if
     * it is not a filter this device runs.
     */
    suspend fun import(context: Context, uri: Uri, pref: Preference<List<WebGpuShader>>) =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment ?: "shader"
            val source = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(MAX_BYTES + 1)
                var size = 0
                while (size < buffer.size) {
                    val read = input.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    size += read
                }
                require(size <= MAX_BYTES) { "$name is over ${MAX_BYTES shr 20} MB" }
                buffer.decodeToString(0, size)
            } ?: throw IllegalArgumentException("cannot open $name")
            ImageFilterWgsl(source, name).validate()

            val shader = WebGpuShader("${UUID.randomUUID()}.wgsl", name.replace(Regex("""[\t\r\n]"""), " "))
            // Past validation, the copy and its entry land together or not at all.
            withContext(NonCancellable) {
                synchronized(lock) {
                    val file = File(dir(context).apply { mkdirs() }, shader.file)
                    try {
                        file.writeText(source)
                        pref.set(pref.get() + shader)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
            }
        }

    fun remove(context: Context, pref: Preference<List<WebGpuShader>>, shader: WebGpuShader) {
        synchronized(lock) {
            pref.set(pref.get().filter { it.file != shader.file })
            File(dir(context), shader.file).delete()
        }
    }

    /**
     * Deletes copies neither of [prefs] names, and entries whose copy is gone - left by a restored
     * backup, or a crash between the two.
     */
    suspend fun prune(context: Context, prefs: List<Preference<List<WebGpuShader>>>) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val dir = dir(context)
                val present = dir.list()?.toSet().orEmpty()
                val named = HashSet<String>()
                for (pref in prefs) {
                    val shaders = pref.get()
                    val kept = shaders.filter { it.file in present }
                    if (kept.size != shaders.size) pref.set(kept)
                    kept.mapTo(named) { it.file }
                }
                for (file in present) {
                    if (file !in named) File(dir, file).delete()
                }
            }
        }

    /** Throws if the file is gone or no longer parses. */
    fun load(context: Context, shader: WebGpuShader): ImageFilterWgsl =
        ImageFilterWgsl(File(dir(context), shader.file).readText(), shader.name)
}
