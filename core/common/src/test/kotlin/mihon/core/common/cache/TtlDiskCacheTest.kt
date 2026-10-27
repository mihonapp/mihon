package mihon.core.common.cache

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TtlDiskCacheTest {

    @TempDir
    lateinit var root: File

    private val directory get() = File(root, "cache")

    private val index get() = File(root, "index/cache.index")

    private var time = 1_000_000_000L

    private fun TestScope.cache(
        ttl: Duration = 1.hours,
        saveDelay: Duration = Duration.ZERO,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ) = TtlDiskCache(directory, index, ttl, backgroundScope, saveDelay, dispatcher, now = { time })

    private suspend fun TtlDiskCache.put(key: String, value: String) = put(key) { it.write(value.toByteArray()) }

    private suspend fun TtlDiskCache.read(key: String) = get(key)?.readText()

    private fun entryFiles() = directory.listFiles().orEmpty().toList()

    @Test
    fun `returns what was put`() = runTest {
        val cache = cache()
        cache.put("a", "one")

        cache.read("a") shouldBe "one"
        cache.open("a")?.use { it.readBytes().decodeToString() } shouldBe "one"
        cache.contains("a") shouldBe true
        cache.contains("b") shouldBe false
        cache.open("b") shouldBe null
    }

    @Test
    fun `replaces a key's value`() = runTest {
        val cache = cache()
        cache.put("a", "one")
        cache.put("a", "two!")

        cache.read("a") shouldBe "two!"
        cache.totalSize() shouldBe 4
    }

    @Test
    fun `expires entries ttl after they were written`() = runTest {
        val cache = cache(ttl = 10.seconds)
        cache.put("a", "one")

        time += 9_000
        cache.read("a") shouldBe "one"

        time += 1_000
        cache.read("a") shouldBe null
        cache.totalSize() shouldBe 0
        entryFiles().size shouldBe 0
    }

    @Test
    fun `removes expired entries as others are written`() = runTest {
        val cache = cache(ttl = 10.seconds)
        cache.put("a", "aaaa")
        time += 5_000
        cache.put("b", "bbbb")
        time += 5_000
        cache.put("c", "cccc")

        cache.totalSize() shouldBe 8
        entryFiles().size shouldBe 2
        cache.read("b") shouldBe "bbbb"
    }

    @Test
    fun `counts a replaced entry's age from its last write`() = runTest {
        val cache = cache(ttl = 10.seconds)
        cache.put("a", "one")
        time += 5_000
        cache.put("a", "two")
        time += 5_000

        cache.read("a") shouldBe "two"
    }

    @Test
    fun `keeps entries and their age across instances`() = runTest {
        cache(ttl = 10.seconds).put("a", "one")
        time += 5_000

        val reopened = cache(ttl = 10.seconds)
        reopened.read("a") shouldBe "one"

        time += 5_000
        reopened.read("a") shouldBe null
    }

    @Test
    fun `takes write times from the index rather than the files`() = runTest {
        val file = cache(ttl = 10.seconds).put("a", "one")
        // As a backup restore or a file system that keeps no times might leave it
        file.setLastModified(0)

        cache(ttl = 10.seconds).read("a") shouldBe "one"
    }

    @Test
    fun `falls back to the files' times without an index`() = runTest {
        val file = cache(ttl = 10.seconds).put("a", "one")
        index.delete()

        file.setLastModified(time)
        cache(ttl = 10.seconds).read("a") shouldBe "one"

        index.delete()
        file.setLastModified(time - 10_000)
        cache(ttl = 10.seconds).read("a") shouldBe null
    }

    @Test
    fun `skips index lines it can't read`() = runTest {
        val cache = cache()
        cache.put("a", "one")
        cache.put("b", "two")
        index.appendText("cut-sho")

        val reopened = cache()
        reopened.read("a") shouldBe "one"
        reopened.read("b") shouldBe "two"
        reopened.totalSize() shouldBe 6
    }

    @Test
    fun `forgets removed entries`() = runTest {
        val cache = cache()
        cache.put("a", "one")
        cache.put("b", "two")
        cache.remove("a")

        val reopened = cache()
        reopened.read("a") shouldBe null
        reopened.read("b") shouldBe "two"
    }

    @Test
    fun `saves a burst of changes once, after the delay`() = runTest {
        val cache = cache(saveDelay = 200.milliseconds)
        cache.put("a", "one")
        cache.put("b", "two")
        cache.put("c", "three")

        index.exists() shouldBe false
        advanceTimeBy(199.milliseconds)
        index.exists() shouldBe false
        advanceTimeBy(1.milliseconds)
        runCurrent()
        index.readLines().size shouldBe 4
    }

    @Test
    fun `handles files deleted behind its back`() = runTest {
        val cache = cache()
        val a = cache.put("a", "one")
        cache.put("b", "two")
        a.delete()

        cache.get("a") shouldBe null
        cache.open("a") shouldBe null
        cache.totalSize() shouldBe 3

        directory.deleteRecursively()
        cache.open("b") shouldBe null
        cache.totalSize() shouldBe 0

        cache.put("c", "three")
        cache.read("c") shouldBe "three"
        cache().read("c") shouldBe "three"
    }

    @Test
    fun `stores nothing when the write fails`() = runTest {
        val cache = cache()
        cache.put("a", "one")

        try {
            cache.put("a") {
                it.write("partial".toByteArray())
                throw IOException()
            }
        } catch (_: IOException) {
        }

        cache.read("a") shouldBe "one"
        entryFiles().size shouldBe 1
    }

    @Test
    fun `deletes writes left unfinished`() = runTest {
        directory.mkdirs()
        File(directory, "abc.tmp-1").writeText("partial")
        index.parentFile.mkdirs()
        val unfinished = File(index.parentFile, "cache.index.tmp-1").apply { writeText("partial") }

        cache().totalSize() shouldBe 0
        entryFiles().size shouldBe 0
        unfinished.exists() shouldBe false
    }

    @Test
    fun `keeps the index when the entries are cleared from outside`() = runTest {
        val cache = cache(ttl = 10.seconds)
        cache.put("a", "one")
        time += 5_000
        cache.put("b", "two")
        directory.deleteRecursively()

        val reopened = cache(ttl = 10.seconds)
        reopened.totalSize() shouldBe 0
        index.exists() shouldBe true

        reopened.put("c", "three")
        time += 5_000
        cache(ttl = 10.seconds).read("c") shouldBe "three"
        // The header and c, a and b dropped as their files were gone
        index.readLines().size shouldBe 2
    }

    @Test
    fun `removes and clears`() = runTest {
        val cache = cache()
        cache.put("a", "one")
        cache.put("b", "two")
        cache.put("c", "three")

        cache.remove("a") shouldBe true
        cache.remove("a") shouldBe false
        cache.clear() shouldBe 2
        cache.get("b") shouldBe null
        cache.totalSize() shouldBe 0
        cache().totalSize() shouldBe 0
    }

    @Test
    fun `keeps every entry written from several threads`() = runTest {
        val cache = cache(dispatcher = Dispatchers.IO)
        (0..<8).map { thread ->
            launch(Dispatchers.Default) {
                repeat(50) { cache.put("$thread-$it", "$thread-$it") }
            }
        }.joinAll()

        (0..<8).forEach { thread ->
            repeat(50) { cache.read("$thread-$it") shouldBe "$thread-$it" }
        }
        entryFiles().size shouldBe 400
        cache.totalSize() shouldBe entryFiles().sumOf { it.length() }
        cache(dispatcher = Dispatchers.IO).totalSize() shouldBe cache.totalSize()
    }

    @Test
    fun `names files after a hash of the key`() = runTest {
        val cache = cache()
        val file = cache.put("https://example.org/a/b.jpg", "x")

        file.name.length shouldBe 64
        file.parentFile shouldBe directory
        cache.get("https://example.org/a/b.jpg") shouldNotBe null
    }

    @Test
    fun `warming up loads the entries before first use`() = runTest {
        cache(ttl = 10.seconds).put("a", "one")
        directory.mkdirs()
        File(directory, "abc.tmp-1").writeText("partial")
        time += 10_000

        cache(ttl = 10.seconds).warmUp()
        entryFiles().size shouldBe 0
    }

    @Test
    fun `expires entries written before the clock went back ttl after it did`() = runTest {
        val cache = cache(ttl = 10.seconds)
        cache.put("a", "aaaa")
        time -= 5_000
        cache.put("b", "bbbb")

        time += 9_000
        cache.read("a") shouldBe "aaaa"
        time += 1_000
        cache.put("c", "cccc")

        cache.read("a") shouldBe null
        cache.read("b") shouldBe null
        entryFiles().size shouldBe 1
        cache.totalSize() shouldBe 4
    }

    @Test
    fun `doesn't rewrite an index that matches the files`() = runTest {
        cache().put("a", "one")
        index.setLastModified(1_000)

        cache().warmUp()
        index.lastModified() shouldBe 1_000
    }

    @Test
    fun `rewrites an index that misses files`() = runTest {
        cache().put("a", "one")
        File(directory, "abc").writeText("two")
        index.setLastModified(1_000)

        val reopened = cache()
        reopened.warmUp()
        index.lastModified() shouldNotBe 1_000
        index.readLines().size shouldBe 3
        reopened.totalSize() shouldBe 6
    }
}
