package mihon.desktop.extension

import com.sun.jna.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class WindowsJobObjectTest {

    @Test
    fun `initializes job object and assigns dummy process on windows`() {
        val job = WindowsJobObject(memoryLimitBytes = 256L * 1024L * 1024L)
        try {
            if (Platform.isWindows()) {
                job.jobHandle shouldNotBe null
                val dummyProc = ProcessBuilder("cmd.exe", "/c", "ping 127.0.0.1 -n 3 > nul").start()
                try {
                    val assigned = job.assignProcess(dummyProc)
                    assigned shouldBe true
                } finally {
                    dummyProc.destroyForcibly()
                }
            } else {
                job.jobHandle shouldBe null
            }
        } finally {
            job.close()
        }
    }
}
