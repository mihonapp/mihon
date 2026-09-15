package mihon.webview

import kotlinx.serialization.json.jsonObject
import kotlin.system.exitProcess

fun main() {
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, Charsets.UTF_8))
    check(Runtime.version().feature() == 21) { "Browser helper requires its independent Java 21 JCEF runtime" }
    val config = wireJson.parseToJsonElement(readln()).jsonObject
    val identity =
        SessionIdentity(config.string("sessionId"), config.string("sourceId").toLong(), config.string("token"))
    val events = Events(identity)
    var failed = false
    try {
        WebViewSession(config, identity, events).use { session ->
            while (true) {
                val line = readlnOrNull() ?: break
                if (line.length > 1_048_576) error("IPC message too large")
                val command = wireJson.parseToJsonElement(line).jsonObject
                if (!identity.accepts(command)) {
                    events.send("error", value = "Session identity mismatch")
                    continue
                }
                if (command.string("type") == "close") break
                session.command(command)
            }
        }
    } catch (failure: Throwable) {
        failed = true
        events.send(
            "error",
            value =
            failure.message ?: failure.javaClass.simpleName,
        )
    }
    exitProcess(if (failed) 1 else 0)
}
