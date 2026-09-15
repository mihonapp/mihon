package mihon.desktop.extension

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

object SandboxAccessProbe {
    @JvmStatic fun main(args: Array<String>) {
        val input = DataInputStream(FileInputStream(args.first { it.startsWith("--pipe-read=") }.substringAfter('=')))
        val output =
            DataOutputStream(FileOutputStream(args.first { it.startsWith("--pipe-write=") }.substringAfter('=')))
        val token = System.getenv("MIHON_IPC_NONCE")
        output.writeUTF(token)
        output.flush()
        check(input.readUTF() == token)
        if ("--memory-probe" in args) {
            val denied = try {
                java.nio.ByteBuffer.allocateDirect(512 * 1024 * 1024)
                false
            } catch (_: OutOfMemoryError) {
                true
            }
            output.writeUTF("memory=$denied")
            output.flush()
            Thread.sleep(60000)
            return
        }
        val deniedRead = runCatching { File(args[0]).readText() }.isFailure
        val deniedWrite = runCatching { File(args[1]).writeText("escaped") }.isFailure
        val deniedNetwork = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", args[2].toInt()), 1000) }
        }.isFailure
        File("allowed.txt").writeText("private work directory")
        output.writeUTF("read=$deniedRead;write=$deniedWrite;network=$deniedNetwork")
        output.flush()
        Thread.sleep(60000)
    }
}
