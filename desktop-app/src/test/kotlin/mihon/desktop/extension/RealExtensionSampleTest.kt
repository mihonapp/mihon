package mihon.desktop.extension

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.extension.host.BrokeredHttpClient
import mihon.extension.host.ExtensionHostEngine
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RealExtensionSampleTest {
    @TempDir lateinit var workingDirectory: Path

    @Test
    fun `real APK inventory converts and records actual host load outcomes`() {
        val location = System.getenv("MIHON_W_REAL_APK_DIR")
        assumeTrue(location != null)
        val root = Path.of(location!!)
        val inventory = Json.parseToJsonElement(Files.readString(root.resolve("inventory.json"))).jsonArray
        val outcomes = mutableListOf<String>()
        var successes = 0
        for (item in inventory) {
            val record = item.jsonObject
            val pkg = record.getValue("package").jsonPrimitive.content
            val file = Path.of(record.getValue("file").jsonPrimitive.content).toFile()
            val parentOutput = java.io.PipedOutputStream()
            val hostInput = java.io.PipedInputStream(parentOutput, 65536)
            val hostOutput = java.io.PipedOutputStream()
            val parentInput = java.io.PipedInputStream(hostOutput, 65536)
            val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
            val parentSession = mihon.extension.ipc.IpcSession(parentInput, parentOutput, onCallback = { callback ->
                if (callback.callbackType == mihon.extension.ipc.IpcCallbacks.BROKER_HTTP) {
                    requests.add(callback.payloadJson)
                    mihon.extension.ipc.IpcCallbackResponse(
                        callback.requestId,
                        true,
                        Json.encodeToString(
                            mihon.extension.ipc.BrokerHttpResponse(
                                200,
                                mapOf("Content-Type" to "text/html"),
                                "<html><body></body></html>",
                            ),
                        ),
                    )
                } else {
                    mihon.extension.ipc.IpcCallbackResponse(
                        callback.requestId,
                        false,
                        error = "External browser disabled in offline fixture",
                    )
                }
            })
            val hostSession = mihon.extension.ipc.IpcSession(hostInput, hostOutput)
            val engine = ExtensionHostEngine(BrokeredHttpClient { hostSession })
            try {
                val converted = Files.createDirectories(root.resolve("converted")).resolve("$pkg.mext").toFile()
                TachiyomiExtensionConverter.convertToMext(file, converted)
                val sources = engine.loadExtension(converted, workingDirectory.resolve(pkg).toFile())
                assertTrue(sources.isNotEmpty())
                successes++
                outcomes.add("$pkg | LOADED | ${sources.size} | ${sources.joinToString { it.id.toString() }}")
                for (command in listOf(
                    mihon.extension.ipc.IpcCommands.GET_FILTER_LIST,
                    mihon.extension.ipc.IpcCommands.GET_SOURCE_PREFERENCES,
                )) {
                    val response = kotlinx.coroutines.runBlocking {
                        engine.handleRequest(
                            mihon.extension.ipc.IpcRequest(
                                requestId = 1,
                                command = command,
                                payloadJson = "{\"sourceId\":${sources.first().id}}",
                            ),
                        )
                    }
                    outcomes.add(
                        "$pkg | $command | ${response.success} | ${if (response.success) response.payloadJson else response.error}",
                    )
                }
                if (!pkg.endsWith("mangafire")) {
                    val response = kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeout(5000) {
                            engine.handleRequest(
                                mihon.extension.ipc.IpcRequest(
                                    2,
                                    mihon.extension.ipc.IpcCommands.GET_POPULAR,
                                    "{\"sourceId\":${sources.first().id},\"page\":1}",
                                ),
                            )
                        }
                    }
                    outcomes.add("$pkg | OFFLINE_POPULAR | ${response.success} | ${response.error}")
                    if (pkg.endsWith("bilimanga")) {
                        val changed = kotlinx.coroutines.runBlocking {
                            engine.handleRequest(
                                mihon.extension.ipc.IpcRequest(
                                    3,
                                    mihon.extension.ipc.IpcCommands.SET_SOURCE_PREFERENCE,
                                    Json.encodeToString(
                                        mihon.extension.ipc.SetSourcePreferencePayload(
                                            sources.first().id,
                                            "POPULAR_MANGA_DISPLAY",
                                            mihon.extension.ipc.SelectPreferenceValueDto("/top/monthvisit/%d.html"),
                                        ),
                                    ),
                                ),
                            )
                        }
                        assertTrue(changed.success, changed.error)
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.withTimeout(5000) {
                                engine.handleRequest(
                                    mihon.extension.ipc.IpcRequest(
                                        4,
                                        mihon.extension.ipc.IpcCommands.GET_POPULAR,
                                        "{\"sourceId\":${sources.first().id},\"page\":1}",
                                    ),
                                )
                            }
                        }
                        outcomes.add(
                            "$pkg | PREFERENCE_REQUEST_CHANGED | ${requests.any {
                                it.contains("/top/weekvisit/1.html")
                            } && requests.any { it.contains("/top/monthvisit/1.html") }}",
                        )
                    }
                    requests.forEach { outcomes.add("$pkg | CAPTURED_REQUEST | $it") }
                }
            } catch (error: Throwable) {
                val causes = generateSequence(error) { it.cause }.take(8).joinToString(" <- ") {
                    "${it.javaClass.simpleName}: ${it.message}"
                }
                outcomes.add("$pkg | FAILED | $causes")
            } finally {
                engine.unloadExtension(pkg)
                hostSession.close()
                parentSession.close()
            }
        }
        Files.write(root.resolve("load-results.txt"), outcomes)
        outcomes.forEach(::println)
        assertTrue(successes == inventory.size, "Not every real APK loaded; see sample load-results.txt")
        assertTrue(
            outcomes.any {
                it.contains("bilimanga | PREFERENCE_REQUEST_CHANGED | true")
            },
            "Real BiliManga preference must change its actual request path",
        )
    }
}
