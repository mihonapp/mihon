@file:Suppress("FunctionName")

package mihon.desktop.extension

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

internal interface SandboxKernel : StdCallLibrary {
    fun InitializeProcThreadAttributeList(list: Pointer?, count: Int, flags: Int, size: Pointer): Boolean
    fun UpdateProcThreadAttribute(
        list: Pointer,
        flags: Int,
        attribute: Long,
        value: Pointer,
        size: Long,
        previous: Pointer?,
        returned: Pointer?,
    ): Boolean
    fun DeleteProcThreadAttributeList(list: Pointer)
    fun CreateProcessW(
        app: WString?,
        command: CharArray,
        processAttributes: Pointer?,
        threadAttributes: Pointer?,
        inherit: Boolean,
        flags: Int,
        environment: Pointer?,
        directory: WString,
        startup: Pointer,
        result: WinBase.PROCESS_INFORMATION,
    ): Boolean
    fun ResumeThread(thread: HANDLE): Int
    fun ProcessIdToSessionId(pid: Int, session: IntByReference): Boolean
    fun GetAppContainerNamedObjectPath(
        token: HANDLE?,
        sid: Pointer,
        length: Int,
        path: CharArray,
        required: IntByReference,
    ): Boolean
    fun CreateNamedPipeW(
        name: WString,
        access: Int,
        mode: Int,
        instances: Int,
        outSize: Int,
        inSize: Int,
        timeout: Int,
        security: WinBase.SECURITY_ATTRIBUTES,
    ): HANDLE
    fun ConnectNamedPipe(pipe: HANDLE, overlapped: Pointer?): Boolean
    fun GetNamedPipeClientProcessId(pipe: HANDLE, pid: IntByReference): Boolean
    fun CancelIoEx(handle: HANDLE, overlapped: Pointer?): Boolean
}
internal interface SandboxUserEnv : StdCallLibrary {
    fun CreateAppContainerProfile(
        name: WString,
        display: WString,
        description: WString,
        capabilities: Pointer?,
        count: Int,
        sid: PointerByReference,
    ): Int
    fun DeleteAppContainerProfile(name: WString): Int
}
internal interface SandboxAdvapi : StdCallLibrary {
    fun ConvertSidToStringSidW(sid: Pointer, result: PointerByReference): Boolean
    fun ConvertStringSecurityDescriptorToSecurityDescriptorW(
        value: WString,
        revision: Int,
        result: PointerByReference,
        size: Pointer?,
    ): Boolean
    fun GetTokenInformation(
        token: HANDLE,
        type: Int,
        information: Pointer?,
        length: Int,
        returned: IntByReference,
    ): Boolean
    fun FreeSid(sid: Pointer): Pointer?
}
internal object WindowsSandboxNative {
    val kernel: SandboxKernel = Native.load("kernel32", SandboxKernel::class.java)
    val userenv: SandboxUserEnv = Native.load("userenv", SandboxUserEnv::class.java)
    val advapi: SandboxAdvapi = Native.load("advapi32", SandboxAdvapi::class.java)
    fun requireSuccess(success: Boolean, operation: String) {
        check(success) { "$operation failed (Win32 ${Native.getLastError()}); sandbox launch aborted" }
    }
    fun tokenIsAppContainer(process: HANDLE): Boolean {
        val token = com.sun.jna.platform.win32.WinNT.HANDLEByReference()
        requireSuccess(
            com.sun.jna.platform.win32.Advapi32.INSTANCE.OpenProcessToken(process, 8, token),
            "OpenProcessToken",
        )
        return try {
            val data = Memory(4)
            requireSuccess(
                advapi.GetTokenInformation(token.value, 29, data, 4, IntByReference()),
                "TokenIsAppContainer",
            )
            data.getInt(0) != 0
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.value)
        }
    }
}
