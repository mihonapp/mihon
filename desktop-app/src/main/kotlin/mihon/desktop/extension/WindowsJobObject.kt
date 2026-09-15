@file:Suppress(
    "ClassName",
    "PropertyName",
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "ktlint:standard:class-naming",
    "ktlint:standard:property-naming",
)

package mihon.desktop.extension

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.win32.W32APIOptions
import java.io.Closeable

@Structure.FieldOrder(
    "PerProcessUserTimeLimit",
    "PerJobUserTimeLimit",
    "LimitFlags",
    "MinimumWorkingSetSize",
    "MaximumWorkingSetSize",
    "ActiveProcessLimit",
    "Affinity",
    "PriorityClass",
    "SchedulingClass",
)
open class JOBOBJECT_BASIC_LIMIT_INFORMATION : Structure() {
    @JvmField var PerProcessUserTimeLimit: Long = 0

    @JvmField var PerJobUserTimeLimit: Long = 0

    @JvmField var LimitFlags: Int = 0

    @JvmField var MinimumWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)

    @JvmField var MaximumWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)

    @JvmField var ActiveProcessLimit: Int = 0

    @JvmField var Affinity: BaseTSD.ULONG_PTR = BaseTSD.ULONG_PTR(0)

    @JvmField var PriorityClass: Int = 0

    @JvmField var SchedulingClass: Int = 0
}

@Structure.FieldOrder(
    "ReadOperationCount",
    "WriteOperationCount",
    "OtherOperationCount",
    "ReadTransferCount",
    "WriteTransferCount",
    "OtherTransferCount",
)
open class IO_COUNTERS : Structure() {
    @JvmField var ReadOperationCount: Long = 0

    @JvmField var WriteOperationCount: Long = 0

    @JvmField var OtherOperationCount: Long = 0

    @JvmField var ReadTransferCount: Long = 0

    @JvmField var WriteTransferCount: Long = 0

    @JvmField var OtherTransferCount: Long = 0
}

@Structure.FieldOrder(
    "BasicLimitInformation",
    "IoInfo",
    "ProcessMemoryLimit",
    "JobMemoryLimit",
    "PeakProcessMemoryLimit",
    "PeakJobMemoryLimit",
)
open class JOBOBJECT_EXTENDED_LIMIT_INFORMATION : Structure() {
    @JvmField var BasicLimitInformation: JOBOBJECT_BASIC_LIMIT_INFORMATION = JOBOBJECT_BASIC_LIMIT_INFORMATION()

    @JvmField var IoInfo: IO_COUNTERS = IO_COUNTERS()

    @JvmField var ProcessMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)

    @JvmField var JobMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)

    @JvmField var PeakProcessMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)

    @JvmField var PeakJobMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
}

@Structure.FieldOrder("ControlFlags", "CpuRate")
open class JOBOBJECT_CPU_RATE_CONTROL_INFORMATION : Structure() {
    @JvmField var ControlFlags: Int = 0

    @JvmField var CpuRate: Int = 0
}

interface JobObjectKernel32 : Kernel32 {
    companion object {
        val INSTANCE: JobObjectKernel32 by lazy {
            Native.load("kernel32", JobObjectKernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    fun CreateJobObject(lpJobAttributes: WinBase.SECURITY_ATTRIBUTES?, lpName: String?): HANDLE?
    fun SetInformationJobObject(
        hJob: HANDLE,
        JobObjectInformationClass: Int,
        lpJobObjectInformation: Pointer,
        cbJobObjectInformationLength: Int,
    ): Boolean
    fun QueryInformationJobObject(
        hJob: HANDLE,
        JobObjectInformationClass: Int,
        lpJobObjectInformation: Pointer,
        cbJobObjectInformationLength: Int,
        lpReturnLength: Pointer?,
    ): Boolean
    fun AssignProcessToJobObject(hJob: HANDLE, hProcess: HANDLE): Boolean
    fun TerminateJobObject(hJob: HANDLE, uExitCode: Int): Boolean
}

class WindowsJobObject(
    memoryLimitBytes: Long = 1024L * 1024L * 1024L,
    activeProcessLimit: Int = 1,
    cpuRatePercent: Int? = null,
) : Closeable {

    companion object {
        const val JobObjectCpuRateControlInformation = 15
        const val JobObjectExtendedLimitInformation = 9
        const val JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
        const val JOB_OBJECT_LIMIT_JOB_MEMORY = 0x0200
    }

    val jobHandle: HANDLE?

    init {
        require(memoryLimitBytes > 0 && activeProcessLimit > 0)
        require(cpuRatePercent == null || cpuRatePercent in 1..100) { "CPU rate must be between 1 and 100 percent" }
        check(cpuRatePercent == null || Platform.isWindows()) {
            "Windows Job CPU hard cap is unavailable on this platform"
        }
        if (Platform.isWindows()) {
            val handle = JobObjectKernel32.INSTANCE.CreateJobObject(null, null)
            if (handle != null && handle != WinBase.INVALID_HANDLE_VALUE) {
                this.jobHandle = handle
                val info = JOBOBJECT_EXTENDED_LIMIT_INFORMATION()
                info.BasicLimitInformation.LimitFlags =
                    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE or JOB_OBJECT_LIMIT_JOB_MEMORY or 0x8
                info.BasicLimitInformation.ActiveProcessLimit = activeProcessLimit
                info.JobMemoryLimit = BaseTSD.SIZE_T(memoryLimitBytes)
                info.write()
                val configured = JobObjectKernel32.INSTANCE.SetInformationJobObject(
                    handle,
                    JobObjectExtendedLimitInformation,
                    info.pointer,
                    info.size(),
                )
                if (!configured) {
                    val code = Native.getLastError()
                    Kernel32.INSTANCE.CloseHandle(handle)
                    error("Unable to configure extension Job Object: $code")
                }
                if (cpuRatePercent != null) {
                    val cpu = JOBOBJECT_CPU_RATE_CONTROL_INFORMATION()
                    cpu.ControlFlags = 0x1 or 0x4 // ENABLE | HARD_CAP; fail closed before the child is resumed.
                    cpu.CpuRate = cpuRatePercent * 100
                    cpu.write()
                    if (!JobObjectKernel32.INSTANCE.SetInformationJobObject(
                            handle,
                            JobObjectCpuRateControlInformation,
                            cpu.pointer,
                            cpu.size(),
                        )
                    ) {
                        val code = Native.getLastError()
                        Kernel32.INSTANCE.CloseHandle(handle)
                        error(
                            "Windows Job CPU hard cap $cpuRatePercent% unavailable (Win32 $code); unbounded fallback forbidden",
                        )
                    }
                }
            } else {
                error("Unable to create extension Job Object: ${Native.getLastError()}")
            }
        } else {
            this.jobHandle = null
        }
    }

    fun assignProcess(process: Process): Boolean {
        if (!Platform.isWindows() || jobHandle == null) return false
        val pid = process.pid().toInt()
        val hProcess = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_SET_QUOTA or WinNT.PROCESS_TERMINATE,
            false,
            pid,
        ) ?: return false

        return try {
            JobObjectKernel32.INSTANCE.AssignProcessToJobObject(jobHandle, hProcess)
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess)
        }
    }

    fun terminate(exitCode: Int = 1): Boolean {
        if (jobHandle == null) return false
        return JobObjectKernel32.INSTANCE.TerminateJobObject(jobHandle, exitCode)
    }

    override fun close() {
        if (jobHandle != null) {
            Kernel32.INSTANCE.CloseHandle(jobHandle)
        }
    }
}
