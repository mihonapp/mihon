package mihon.core.common.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A disk cache whose entries expire [ttl] after they're written, which is all that bounds its size.
 *
 * An entry is one file named after its key's hash. It's written to a temporary file and moved into place, so a
 * crash or a failed write leaves either the old entry or none, never a partial one.
 *
 * The [indexFile] records when each entry was written. It's kept apart from the entries, so that clearing them, as
 * the system does with cache directories, leaves it in place, and it's saved in [scope] [saveDelay] after a change
 * so a burst of writes saves it once. Loading reconciles it with the files there are, so an index that's lost or out
 * of date only costs the write times it missed, which then come from the files' modification times.
 */
class TtlDiskCache(
    val directory: File,
    private val indexFile: File,
    private val ttl: Duration,
    scope: CoroutineScope,
    private val saveDelay: Duration = 2.seconds,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private class Entry(val file: File, val size: Long, val writtenAt: Long)

    private val mutex = Mutex()

    // In the order they were written, so the expired ones come first
    private val entries = LinkedHashMap<String, Entry>()

    private var loaded = false

    private var size = 0L

    private var latestWrite = 0L

    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(dispatcher) {
            for (request in saveRequests) {
                delay(saveDelay)
                mutex.withLock { save() }
            }
        }
    }

    /**
     * Loads the entries now, rather than on first use.
     */
    suspend fun warmUp() = locked {}

    /**
     * Bytes the entries take.
     */
    suspend fun totalSize(): Long = locked {
        // The system deletes cache files when storage runs low
        entries.values.filterNot { it.file.exists() }.forEach { remove(it) }
        size
    }

    /**
     * The file holding [key]'s value, or null when there's none, it has expired or it was deleted.
     */
    suspend fun get(key: String): File? = locked {
        val entry = entries[fileName(key)] ?: return@locked null
        if (entry.isExpired() || !entry.file.exists()) {
            remove(entry)
            return@locked null
        }
        entry.file
    }

    /**
     * Opens [key]'s value, or returns null when there's none, it has expired or it was deleted.
     */
    suspend fun open(key: String): InputStream? {
        val file = get(key) ?: return null
        return withContext(dispatcher) {
            try {
                file.inputStream()
            } catch (_: FileNotFoundException) {
                // Deleted since it was looked up
                mutex.withLock { entries[file.name]?.takeUnless { it.file.exists() }?.let { remove(it) } }
                null
            }
        }
    }

    suspend operator fun contains(key: String): Boolean = get(key) != null

    /**
     * Stores what [write] writes as [key]'s value, replacing any there is. Nothing is stored when [write] throws.
     */
    @Throws(IOException::class)
    suspend fun put(key: String, write: (OutputStream) -> Unit): File = withContext(dispatcher) {
        // Before the temporary file exists, as loading deletes those left over
        mutex.withLock { load() }
        val name = fileName(key)
        val temp = tempFile(directory, name)
        try {
            val out = try {
                directory.mkdirs()
                temp.outputStream()
            } catch (_: FileNotFoundException) {
                // The system deleted the directory in between
                directory.mkdirs()
                temp.outputStream()
            }
            out.use(write)
            val file = File(directory, name)
            mutex.withLock {
                move(temp, file)
                entries.remove(name)?.let { size -= it.size }
                val entry = Entry(file, file.length(), now())
                entries[name] = entry
                size += entry.size
                latestWrite = maxOf(latestWrite, entry.writtenAt)
                removeExpired()
                changed()
            }
            file
        } finally {
            temp.delete()
        }
    }

    suspend fun remove(key: String): Boolean = locked {
        val entry = entries[fileName(key)] ?: return@locked false
        remove(entry)
        true
    }

    /**
     * Removes every entry, returning how many there were.
     */
    suspend fun clear(): Int = locked {
        val count = entries.size
        entries.values.forEach { it.file.delete() }
        entries.clear()
        size = 0
        save()
        count
    }

    /**
     * Runs [block] on the [dispatcher] holding the lock, once the entries are loaded.
     */
    private suspend inline fun <T> locked(crossinline block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            load()
            settleClock()
            block()
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true

        // Left by a save that never finished
        indexFile.parentFile
            ?.listFiles { file -> file.name.startsWith("${indexFile.name}.$TEMP_INFIX") }
            ?.forEach { it.delete() }

        val indexed = readIndex()
        var unindexed = false
        directory.listFiles()
            .orEmpty()
            .mapNotNull { file ->
                when {
                    // Left by a write that never finished
                    TEMP_INFIX in file.name -> null.also { file.delete() }
                    // Written after the index was last saved
                    else -> indexed[file.name]
                        ?: Entry(file, file.length(), file.lastModified()).also { unindexed = true }
                }
            }
            .sortedBy { it.writtenAt }
            .forEach {
                entries[it.file.name] = it
                size += it.size
                latestWrite = maxOf(latestWrite, it.writtenAt)
            }

        if (unindexed || entries.size != indexed.size) changed()
        removeExpired()
    }

    /**
     * Treats entries written after the current time, which the clock going back leaves, as written now. They then
     * expire [ttl] from now rather than also after the skew, and stay in write order for [removeExpired].
     */
    private fun settleClock() {
        val time = now()
        if (time >= latestWrite) return
        for (item in entries.entries) {
            if (item.value.writtenAt > time) item.setValue(Entry(item.value.file, item.value.size, time))
        }
        latestWrite = time
        changed()
    }

    /**
     * The entries the index holds. Lines it can't read are skipped.
     */
    private fun readIndex(): Map<String, Entry> {
        val indexed = HashMap<String, Entry>()
        try {
            indexFile.useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext() || iterator.next() != HEADER) return indexed
                iterator.forEach { line ->
                    val parts = line.split(' ')
                    if (parts.size != 3) return@forEach
                    val writtenAt = parts[1].toLongOrNull() ?: return@forEach
                    val size = parts[2].toLongOrNull() ?: return@forEach
                    indexed[parts[0]] = Entry(File(directory, parts[0]), size, writtenAt)
                }
            }
        } catch (_: IOException) {
            // No index yet, or one that can't be read; the files' modification times stand in
        }
        return indexed
    }

    private fun removeExpired() {
        settleClock()
        val iterator = entries.values.iterator()
        var removed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.isExpired()) break
            iterator.remove()
            entry.file.delete()
            size -= entry.size
            removed = true
        }
        if (removed) changed()
    }

    private fun remove(entry: Entry) {
        entries.remove(entry.file.name)
        entry.file.delete()
        size -= entry.size
        changed()
    }

    private fun changed() {
        if (saveDelay <= Duration.ZERO) {
            save()
        } else {
            saveRequests.trySend(Unit)
        }
    }

    /**
     * Writes the index as the entries there are now.
     */
    private fun save() {
        val parent = indexFile.parentFile ?: return
        val temp = tempFile(parent, indexFile.name)
        try {
            parent.mkdirs()
            temp.bufferedWriter().use { writer ->
                writer.write(HEADER)
                writer.write("\n")
                entries.values.forEach {
                    writer.write("${it.file.name} ${it.writtenAt} ${it.size}\n")
                }
            }
            move(temp, indexFile)
        } catch (_: IOException) {
            // The old index stays, and the next load reconciles it with the files
        } finally {
            temp.delete()
        }
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun tempFile(parent: File, name: String) = File(parent, "$name.$TEMP_INFIX${UUID.randomUUID()}")

    private fun Entry.isExpired() = now() - writtenAt >= ttl.inWholeMilliseconds

    private fun fileName(key: String) = key.encodeUtf8().sha256().hex()

    private companion object {
        const val HEADER = "mihon.TtlDiskCache 1"
        const val TEMP_INFIX = "tmp-"
    }
}
