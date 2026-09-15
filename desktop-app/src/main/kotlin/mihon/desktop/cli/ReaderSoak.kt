package mihon.desktop.cli

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.reader.memory.ReaderMemoryMetrics
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ReaderSoakSummary(
    val processId: Long,
    val elapsedSeconds: Double,
    val cycles: Long,
    val decodedTiles: Long,
    val heapHighWaterBytes: Long,
    val samplesFile: String,
)

@Serializable
private data class ReaderSoakSample(
    val processId: Long,
    val elapsedSeconds: Double,
    val cycles: Long,
    val decodedTiles: Long,
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
    val coreReservedBytes: Long,
    val coreCacheBytes: Long,
    val coreLimitBytes: Long,
)

internal object ReaderSoak {
    fun seconds(environment: Map<String, String>): Long {
        if (environment["MIHON_W_READER_VERIFY"] != "1") return 0
        val text = environment["MIHON_W_READER_SOAK_SECONDS"] ?: return 0
        return requireNotNull(text.toLongOrNull()) { "MIHON_W_READER_SOAK_SECONDS must be an integer" }.also {
            require(it in 1..3600) { "Reader soak duration must be between 1 and 3600 seconds" }
        }
    }

    suspend fun run(
        seconds: Long,
        samples: Path,
        metrics: () -> ReaderMemoryMetrics,
        cycle: suspend () -> Int,
    ): ReaderSoakSummary {
        val started = System.nanoTime()
        val pid = ProcessHandle.current().pid()
        var cycles = 0L
        var tiles = 0L
        var heapHighWater = 0L
        var lastSample = Long.MIN_VALUE
        Files.createDirectories(samples.toAbsolutePath().parent)
        Files.newBufferedWriter(samples, java.nio.file.StandardOpenOption.CREATE_NEW).use { writer ->
            fun sample(force: Boolean = false) {
                val elapsed = System.nanoTime() - started
                if (!force && lastSample != Long.MIN_VALUE && elapsed - lastSample < 1_000_000_000L) return
                lastSample = elapsed
                val heap = Runtime.getRuntime()
                val core = metrics()
                check(core.reservedBytes + core.cacheBytes <= core.limitBytes) { "Reader soak exceeded core budget" }
                heapHighWater = maxOf(heapHighWater, (heap.totalMemory() - heap.freeMemory()))
                writer.appendLine(
                    Json.encodeToString(
                        ReaderSoakSample(
                            pid, elapsed / 1e9, cycles, tiles,
                            (
                                heap.totalMemory() -
                                    heap.freeMemory()
                                ),
                            heap.totalMemory(), core.reservedBytes, core.cacheBytes, core.limitBytes,
                        ),
                    ),
                )
                writer.flush()
            }
            sample(true)
            do {
                currentCoroutineContext().ensureActive()
                tiles += cycle()
                cycles++
                sample()
            } while (System.nanoTime() - started < seconds * 1_000_000_000L)
            sample(true)
        }
        return ReaderSoakSummary(
            pid,
            (System.nanoTime() - started) / 1e9,
            cycles,
            tiles,
            heapHighWater,
            samples.toAbsolutePath().toString(),
        )
    }
}
