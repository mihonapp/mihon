package mihon.desktop.extension

import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class WindowsAuthenticatedPipe(containerSid: String, private val namespacePath: String? = null) : Closeable {
    private val native = WindowsSandboxNative
    private val prefix = "\\\\.\\pipe\\LOCAL\\mihonw-${UUID.randomUUID()}"
    val readName = "$prefix-out"
    val writeName = "$prefix-in"
    val nonce = UUID.randomUUID().toString() + UUID.randomUUID().toString()
    private val ownerSid: String = run {
        val token = WinNT.HANDLEByReference()
        native.requireSuccess(
            Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), 8, token),
            "OpenProcessToken",
        )
        try {
            Advapi32Util.getTokenAccount(token.value).sidString
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.value)
        }
    }
    private val outgoingDelegate = lazy { create(readName, 2, containerSid) }
    private val incomingDelegate = lazy { create(writeName, 1, containerSid) }
    private val outgoing by outgoingDelegate
    private val incoming by incomingDelegate
    fun prepare() {
        outgoing
        incoming
    }
    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b) < 0) -1 else b[0].toInt() and 255
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val buffer = ByteArray(minOf(length, 65536))
            val read = IntByReference()
            if (!Kernel32.INSTANCE.ReadFile(incoming, buffer, buffer.size, read, null)) {
                val error = Kernel32.INSTANCE.GetLastError()
                if (error == 109 || error == 995 || error == 6) return -1
                error("Named pipe read failed: Win32 $error")
            }
            buffer.copyInto(bytes, offset, 0, read.value)
            return if (read.value == 0) -1 else read.value
        }
        override fun close() = this@WindowsAuthenticatedPipe.close()
    }
    val output: OutputStream = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var sent = 0
            while (sent < length) {
                val part = bytes.copyOfRange(offset + sent, offset + minOf(length, sent + 65536))
                val written = IntByReference()
                native.requireSuccess(
                    Kernel32.INSTANCE.WriteFile(outgoing, part, part.size, written, null),
                    "Named pipe write",
                )
                check(written.value > 0)
                sent += written.value
            }
        }
        override fun close() = this@WindowsAuthenticatedPipe.close()
    }

    @Volatile private var closed = false

    private fun create(name: String, direction: Int, sid: String): WinNT.HANDLE {
        val sd = PointerByReference()
        native.requireSuccess(
            native.advapi.ConvertStringSecurityDescriptorToSecurityDescriptorW(
                WString("D:P(A;;GA;;;SY)(A;;GA;;;$ownerSid)(A;;GRGW;;;$sid)S:(ML;;NW;;;LW)"),
                1,
                sd,
                null,
            ),
            "Create pipe security descriptor",
        )
        try {
            val attrs = WinBase.SECURITY_ATTRIBUTES().apply {
                lpSecurityDescriptor = sd.value
                bInheritHandle = false
                write()
            }
            val serverName =
                namespacePath?.let { "\\\\.\\pipe\\${it.trimStart('\\')}\\${name.substringAfterLast('\\')}" } ?: name
            val pipe = native.kernel.CreateNamedPipeW(
                WString(serverName),
                direction or 0x80000,
                8,
                1,
                65536,
                65536,
                10000,
                attrs,
            )
            native.requireSuccess(pipe != WinBase.INVALID_HANDLE_VALUE, "CreateNamedPipe")
            return pipe
        } finally {
            Kernel32.INSTANCE.LocalFree(sd.value)
        }
    }

    fun authenticate(expectedPid: Long) {
        CompletableFuture.runAsync {
            for (pipe in listOf(outgoing, incoming)) {
                val connected = native.kernel.ConnectNamedPipe(pipe, null)
                native.requireSuccess(connected || Kernel32.INSTANCE.GetLastError() == 535, "ConnectNamedPipe")
                val actual = IntByReference()
                native.requireSuccess(
                    native.kernel.GetNamedPipeClientProcessId(pipe, actual),
                    "GetNamedPipeClientProcessId",
                )
                check(actual.value.toLong() == expectedPid) { "Named pipe peer PID does not match launched host" }
            }
            check(DataInputStream(input).readUTF() == nonce) { "Named pipe authentication failed" }
            DataOutputStream(output).apply {
                writeUTF(nonce)
                flush()
            }
        }.get(15, TimeUnit.SECONDS)
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        for (handle in listOf(incomingDelegate, outgoingDelegate).filter { it.isInitialized() }.map { it.value }) {
            native.kernel.CancelIoEx(handle, null)
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }
}
