package mihon.extension.ipc

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class IpcSessionTest {

    @Test
    fun `frame codec encodes and decodes round-trip`() {
        val request = IpcRequest(requestId = 42L, command = IpcCommands.PING, payloadJson = "{\"hello\":\"world\"}")
        val baos = ByteArrayOutputStream()
        IpcFrameCodec.writeMessage(baos, request)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val decoded = IpcFrameCodec.readMessage(bais) as IpcRequest

        decoded.requestId shouldBe 42L
        decoded.command shouldBe IpcCommands.PING
        decoded.payloadJson shouldBe "{\"hello\":\"world\"}"
    }

    @Test
    fun `frame codec handles multiple consecutive messages on stream`() {
        val baos = ByteArrayOutputStream()
        val m1 = IpcRequest(1L, "cmd1")
        val m2 = IpcResponse(1L, true, "res1")
        val m3 = IpcCallbackRequest(2L, "cb1")

        IpcFrameCodec.writeMessage(baos, m1)
        IpcFrameCodec.writeMessage(baos, m2)
        IpcFrameCodec.writeMessage(baos, m3)

        val bais = ByteArrayInputStream(baos.toByteArray())
        (IpcFrameCodec.readMessage(bais) as IpcRequest).command shouldBe "cmd1"
        (IpcFrameCodec.readMessage(bais) as IpcResponse).payloadJson shouldBe "res1"
        (IpcFrameCodec.readMessage(bais) as IpcCallbackRequest).callbackType shouldBe "cb1"
        IpcFrameCodec.readMessage(bais) shouldBe null
    }

    @Test
    fun `request response correlation over duplex pipe`(): Unit = runBlocking {
        // Main -> Host pipe
        val mainOut = PipedOutputStream()
        val hostIn = PipedInputStream(mainOut)
        // Host -> Main pipe
        val hostOut = PipedOutputStream()
        val mainIn = PipedInputStream(hostOut)

        val hostSession = IpcSession(
            input = hostIn,
            output = hostOut,
            onRequest = { req ->
                when (req.command) {
                    "blocked" -> throw IllegalStateException(
                        "wrapped",
                        IpcException(
                            "HTTP error 403",
                            networkFailure = NetworkFailure(NetworkFailureKind.SITE_BLOCKED, 403, "test.invalid"),
                        ),
                    )
                    "echo" -> IpcResponse(req.requestId, success = true, payloadJson = "echoed:${req.payloadJson}")
                    "slow" -> {
                        delay(20)
                        IpcResponse(req.requestId, success = true, payloadJson = "slow_done")
                    }
                    else -> IpcResponse(req.requestId, success = false, error = "Unknown command")
                }
            },
        )

        val mainSession = IpcSession(
            input = mainIn,
            output = mainOut,
        )

        try {
            // Test single request
            val echoRes = mainSession.sendRequest("echo", "test1")
            echoRes shouldBe "echoed:test1"

            // Test concurrent requests
            val deferreds = (1..10).map { i ->
                async(Dispatchers.IO) {
                    mainSession.sendRequest("echo", "val$i")
                }
            }
            val results = deferreds.awaitAll()
            results.size shouldBe 10
            for (i in 1..10) {
                results.contains("echoed:val$i") shouldBe true
            }

            // Test error response
            val errorEx = assertThrows<IpcException> {
                mainSession.sendRequest("unknown_cmd")
            }
            errorEx.message shouldContain "Unknown command"
            assertThrows<IpcException> { mainSession.sendRequest("blocked") }.networkFailure shouldBe
                NetworkFailure(NetworkFailureKind.SITE_BLOCKED, 403, "test.invalid")
        } finally {
            mainSession.close()
            hostSession.close()
        }
    }

    @Test
    fun `bidirectional callback from host to main`(): Unit = runBlocking {
        val mainOut = PipedOutputStream()
        val hostIn = PipedInputStream(mainOut)
        val hostOut = PipedOutputStream()
        val mainIn = PipedInputStream(hostOut)

        var hostSessionRef: IpcSession? = null

        val mainSession = IpcSession(
            input = mainIn,
            output = mainOut,
            onCallback = { cb ->
                if (cb.callbackType == IpcCallbacks.BROKER_HTTP) {
                    IpcCallbackResponse(cb.requestId, success = true, payloadJson = "{\"status\":200,\"body\":\"ok\"}")
                } else {
                    IpcCallbackResponse(cb.requestId, success = false, error = "unsupported callback")
                }
            },
        )

        val hostSession = IpcSession(
            input = hostIn,
            output = hostOut,
            onRequest = { req ->
                if (req.command == "fetch_via_broker") {
                    val brokerRes = hostSessionRef!!.sendCallback(IpcCallbacks.BROKER_HTTP, "{\"url\":\"http://test\"}")
                    IpcResponse(req.requestId, success = true, payloadJson = brokerRes)
                } else {
                    IpcResponse(req.requestId, success = false, error = "bad command")
                }
            },
        )
        hostSessionRef = hostSession

        try {
            val res = mainSession.sendRequest("fetch_via_broker")
            res shouldContain "\"status\":200"
        } finally {
            mainSession.close()
            hostSession.close()
        }
    }

    @Test
    fun `ipc session timeout triggers exception`(): Unit = runBlocking {
        val mainOut = PipedOutputStream()
        val hostIn = PipedInputStream(mainOut)
        val hostOut = PipedOutputStream()
        val mainIn = PipedInputStream(hostOut)

        val hostSession = IpcSession(
            input = hostIn,
            output = hostOut,
            onRequest = { req ->
                delay(500) // Sleep longer than timeout
                IpcResponse(req.requestId, success = true, payloadJson = "late")
            },
        )

        val mainSession = IpcSession(
            input = mainIn,
            output = mainOut,
        )

        try {
            assertThrows<IpcException> {
                mainSession.sendRequest("slow", timeoutMillis = 50)
            }
        } finally {
            mainSession.close()
            hostSession.close()
        }
    }
}
