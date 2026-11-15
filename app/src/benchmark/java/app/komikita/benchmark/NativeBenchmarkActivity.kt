package app.komikita.benchmark

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import app.komikita.core.nativecore.NativeChapterEngine
import tachiyomi.domain.chapter.service.missingChaptersCountKotlin
import java.io.File
import kotlin.random.Random
import kotlin.system.measureNanoTime

/** Measures the complete List -> DoubleArray -> JNI call, exclusively in benchmark builds. */
class NativeBenchmarkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "Native benchmark running" }
        setContentView(label)
        Thread {
            try {
                val csv = StringBuilder("chapters,input,kotlin_median_ns,jni_including_conversion_median_ns\n")
                for (size in listOf(128, 256, 512, 1_000, 10_000)) {
                    val ascending = List(size) { (it + 1) * 2.0 }
                    val mixed = List(size) { index ->
                        val offset = index % 10
                        val chapter = (index / 10 * 12 + offset).toDouble()
                        when (offset) {
                            0 -> -1.0
                            2 -> chapter - 0.5
                            else -> chapter
                        }
                    }.shuffled(Random(size))

                    val inputs = listOf(
                        "ascending" to ascending,
                        "descending" to ascending.reversed(),
                        "mixed" to mixed,
                    )
                    for ((name, numbers) in inputs) {
                        val (kotlin, jni) = measure(numbers)
                        csv.appendLine("$size,$name,$kotlin,$jni")
                    }
                }
                val directory = requireNotNull(getExternalFilesDir(null))
                File(directory, "native-boundary.csv").writeText(csv.toString())
                File(directory, "native-environment.txt").writeText(
                    "API=${Build.VERSION.SDK_INT}\nABI=${Build.SUPPORTED_ABIS.joinToString()}\n" +
                        "device=${Build.MODEL}\nheap=${Runtime.getRuntime().maxMemory()}\n",
                )
                runOnUiThread { label.text = "Native benchmark complete" }
            } catch (e: Throwable) {
                File(requireNotNull(getExternalFilesDir(null)), "native-error.txt").writeText(e.stackTraceToString())
                runOnUiThread { label.text = "Native benchmark failed" }
            }
        }.start()
    }

    private fun measure(numbers: List<Double>): Pair<Long, Long> {
        val expected = numbers.missingChaptersCountKotlin()
        check(NativeChapterEngine.missingChaptersCount(numbers) == expected) {
            "Native library unavailable or result differs"
        }

        fun native() {
            sink = NativeChapterEngine.missingChaptersCount(numbers)!!
        }
        fun kotlin() {
            sink = numbers.missingChaptersCountKotlin()
        }
        repeat(100) {
            native()
            kotlin()
        }

        val kotlinTimes = LongArray(21)
        val jniTimes = LongArray(21)
        repeat(21) { index ->
            fun measureKotlin() {
                kotlinTimes[index] = measureNanoTime { repeat(100) { kotlin() } } / 100
            }
            fun measureJni() {
                jniTimes[index] = measureNanoTime { repeat(100) { native() } } / 100
            }
            if (index % 2 == 0) {
                measureKotlin()
                measureJni()
            } else {
                measureJni()
                measureKotlin()
            }
        }
        kotlinTimes.sort()
        jniTimes.sort()
        return kotlinTimes[kotlinTimes.size / 2] to jniTimes[jniTimes.size / 2]
    }

    companion object {
        @Volatile
        private var sink = 0
    }
}
