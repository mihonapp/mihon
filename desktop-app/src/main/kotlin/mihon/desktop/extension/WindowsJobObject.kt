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
    fun AssignProcessToJobObject(hJob: HANDLE, hProcess: HANDLE): Boolean
    fun TerminateJobObject(hJob: HANDLE, uExitCode: Int): Boolean
}

class WindowsJobObject(
    memoryLimitBytes: Long = 1024L * 1024L * 1024L,
) : Closeable {

    companion object {
        const val JobObjectExtendedLimitInformation = 9
        const val JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
        const val JOB_OBJECT_LIMIT_JOB_MEMORY = 0x0200
    }

    val jobHandle: HANDLE?

    init {
        if (Platform.isWindows()) {
            val handle = JobObjectKernel32.INSTANCE.CreateJobObject(null, null)
            if (handle != null && handle != WinBase.INVALID_HANDLE_VALUE) {
                this.jobHandle = handle
                val info = JOBOBJECT_EXTENDED_LIMIT_INFORMATION()
                info.BasicLimitInformation.LimitFlags =
                    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE or JOB_OBJECT_LIMIT_JOB_MEMORY
                info.JobMemoryLimit = BaseTSD.SIZE_T(memoryLimitBytes)
                info.write()
                JobObjectKernel32.INSTANCE.SetInformationJobObject(
                    handle,
                    JobObjectExtendedLimitInformation,
                    info.pointer,
                    info.size(),
                )
            } else {
                this.jobHandle = null
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
