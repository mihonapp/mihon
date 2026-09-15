package mihon.extension.ipc

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class IpcCancellationTest {
    @Test
    fun `cancelling one request stops its remote callback without stopping other requests`(): Unit = runBlocking {
        val mainOutput = PipedOutputStream()
        val hostInput = PipedInputStream(mainOutput, 65536)
        val hostOutput = PipedOutputStream()
        val mainInput = PipedInputStream(hostOutput, 65536)
        val callbackStarted = CompletableDeferred<Unit>()
        val callbackStopped = CompletableDeferred<Unit>()
        val requestStopped = CompletableDeferred<Unit>()
        var host: IpcSession? = null
        val main = IpcSession(mainInput, mainOutput, onCallback = { callback ->
            callbackStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                callbackStopped.complete(Unit)
            }
        })
        host = IpcSession(hostInput, hostOutput, onRequest = { request ->
            if (request.command == "ping") {
                IpcResponse(request.requestId, true, "pong")
            } else {
                try {
                    host!!.sendCallback("network", timeoutMillis = 10000)
                    IpcResponse(request.requestId, true)
                } finally {
                    requestStopped.complete(Unit)
                }
            }
        })
        try {
            val loading = async { main.sendRequest("load", timeoutMillis = 10000) }
            withTimeout(2000) { callbackStarted.await() }
            loading.cancelAndJoin()
            withTimeout(1000) {
                requestStopped.await()
                callbackStopped.await()
            }
            main.sendRequest("ping") shouldBe "pong"
        } finally {
            main.close()
            host.close()
        }
    }
}
