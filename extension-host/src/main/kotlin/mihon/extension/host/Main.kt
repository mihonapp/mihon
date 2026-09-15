package mihon.extension.host

import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.IpcSession
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("mihon.extension.host", "true")
    // Windows redirects GetTempPath into this AppContainer's private profile. With a
    // relocated LOCALAPPDATA that directory is not provisioned by the OS.
    val temporaryDirectory = File(System.getProperty("java.io.tmpdir"))
    check(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs()) {
        "Cannot create extension temporary directory: $temporaryDirectory"
    }
    var pipeName: String? = null
    var useStdio = false

    for (arg in args) {
        when {
            arg.startsWith("--pipe=") -> pipeName = arg.substringAfter("--pipe=")
            arg == "--stdio" -> useStdio = true
        }
    }

    args.firstOrNull { it.startsWith("--stderr=") }?.substringAfter("=")?.let {
        System.setErr(java.io.PrintStream(FileOutputStream(it, true), true, "UTF-8"))
    }
    val isolatedRead = args.firstOrNull { it.startsWith("--pipe-read=") }?.substringAfter("=")
    val isolatedWrite = args.firstOrNull { it.startsWith("--pipe-write=") }?.substringAfter("=")
    val (input, output) = try {
        when {
            isolatedRead != null && isolatedWrite != null -> {
                val token = requireNotNull(System.getenv("MIHON_IPC_NONCE")) { "Missing IPC authentication token" }
                val read = FileInputStream(isolatedRead)
                val write = FileOutputStream(isolatedWrite)
                java.io.DataOutputStream(write).apply {
                    writeUTF(token)
                    flush()
                }
                check(java.io.DataInputStream(read).readUTF() == token) { "IPC server authentication failed" }
                read to write
            }
            pipeName != null -> openPipe(pipeName)
            useStdio -> System.`in` to System.out
            else -> {
                System.err.println("Usage: extension-host [--pipe=\\\\.\\pipe\\<name> | --stdio]")
                exitProcess(1)
            }
        }
    } catch (e: Exception) {
        System.err.println("Failed to open communication channel: ${e.message}")
        exitProcess(2)
    }

    var session: IpcSession? = null
    val httpClient = BrokeredHttpClient { session }
    val engine = ExtensionHostEngine(httpClient)

    session = IpcSession(
        input = input,
        output = output,
        onRequest = { request ->
            engine.handleRequest(request)
        },
    )

    runBlocking {
        try {
            session.awaitTermination()
        } finally {
            session.close()
        }
    }
}

private fun openPipe(pipeName: String): Pair<InputStream, OutputStream> {
    val path = if (pipeName.startsWith("""\\.\pipe\""")) pipeName else """\\.\pipe\$pipeName"""
    val raf = RandomAccessFile(File(path), "rw")
    val inStream = FileInputStream(raf.fd)
    val outStream = FileOutputStream(raf.fd)
    return inStream to outStream
}
