package mihon.desktop.extension

import com.sun.jna.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class WindowsJobCpuLimitTest {
    @Test
    fun `native kernel confirms configurable hard cap and uncapped browser default`() {
        assumeTrue(Platform.isWindows())
        for (percent in listOf(null, 25, 50)) {
            WindowsJobObject(cpuRatePercent = percent).use { job ->
                val cpu = JOBOBJECT_CPU_RATE_CONTROL_INFORMATION()
                assertTrue(
                    JobObjectKernel32.INSTANCE.QueryInformationJobObject(
                        requireNotNull(job.jobHandle),
                        WindowsJobObject.JobObjectCpuRateControlInformation,
                        cpu.pointer,
                        cpu.size(),
                        null,
                    ),
                )
                cpu.read()
                assertEquals(if (percent == null) 0 else 5, cpu.ControlFlags)
                assertEquals((percent ?: 0) * 100, cpu.CpuRate)
                println("Native Job CPU flags=${cpu.ControlFlags} rate=${cpu.CpuRate}")
            }
        }
    }

    @Test
    fun `invalid CPU rates cannot create an unbounded job`() {
        for (percent in listOf(0, -1, 101)) {
            assertThrows(IllegalArgumentException::class.java) { WindowsJobObject(cpuRatePercent = percent) }
        }
    }
}
