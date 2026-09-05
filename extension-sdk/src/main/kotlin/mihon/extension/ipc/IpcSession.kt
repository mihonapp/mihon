package mihon.extension.ipc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class IpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class IpcSession(
    private val input: InputStream,
    private val output: OutputStream,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val onRequest: (suspend (IpcRequest) -> IpcResponse)? = null,
    private val onCallback: (suspend (IpcCallbackRequest) -> IpcCallbackResponse)? = null,
) : Closeable {

    private val nextRequestId = AtomicLong(1L)
    private val writeMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<Long, CompletableDeferred<IpcResponse>>()
    private val pendingCallbackResponses = ConcurrentHashMap<Long, CompletableDeferred<IpcCallbackResponse>>()

    private val readerJob: Job = scope.launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val message = IpcFrameCodec.readMessage(input) ?: break
                when (message) {
                    is IpcResponse -> {
                        pendingResponses.remove(message.requestId)?.complete(message)
                    }
                    is IpcCallbackResponse -> {
                        pendingCallbackResponses.remove(message.requestId)?.complete(message)
                    }
                    is IpcRequest -> {
                        scope.launch {
                            val response = try {
                                onRequest?.invoke(message) ?: IpcResponse(
                                    requestId = message.requestId,
                                    success = false,
                                    error = "No request handler registered",
                                )
                            } catch (e: Exception) {
                                IpcResponse(
                                    requestId = message.requestId,
                                    success = false,
                                    error = e.message ?: e::class.java.simpleName,
                                )
                            }
                            writeMessage(response)
                        }
                    }
                    is IpcCallbackRequest -> {
                        scope.launch {
                            val response = try {
                                onCallback?.invoke(message) ?: IpcCallbackResponse(
                                    requestId = message.requestId,
                                    success = false,
                                    error = "No callback handler registered",
                                )
                            } catch (e: Exception) {
                                IpcCallbackResponse(
                                    requestId = message.requestId,
                                    success = false,
                                    error = e.message ?: e::class.java.simpleName,
                                )
                            }
                            writeMessage(response)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                // Connection broken
            }
        } finally {
            val error = IpcException("IPC connection terminated")
            pendingResponses.values.forEach { it.completeExceptionally(error) }
            pendingResponses.clear()
            pendingCallbackResponses.values.forEach { it.completeExceptionally(error) }
            pendingCallbackResponses.clear()
        }
    }

    private suspend fun writeMessage(message: IpcMessage) {
        writeMutex.withLock {
            IpcFrameCodec.writeMessage(output, message)
        }
    }

    suspend fun sendRequest(
        command: String,
        payloadJson: String = "",
        timeoutMillis: Long = 30_000L,
    ): String {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<IpcResponse>()
        pendingResponses[id] = deferred
        try {
            writeMessage(IpcRequest(requestId = id, command = command, payloadJson = payloadJson))
            val response = withTimeout(timeoutMillis) {
                deferred.await()
            }
            if (!response.success) {
                throw IpcException(response.error ?: "IPC request failed without error description")
            }
            return response.payloadJson
        } catch (e: Exception) {
            pendingResponses.remove(id)
            if (e is IpcException) throw e
            throw IpcException("IPC request '$command' failed: ${e.message}", e)
        }
    }

    suspend fun sendCallback(
        callbackType: String,
        payloadJson: String = "",
        timeoutMillis: Long = 30_000L,
    ): String {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<IpcCallbackResponse>()
        pendingCallbackResponses[id] = deferred
        try {
            writeMessage(IpcCallbackRequest(requestId = id, callbackType = callbackType, payloadJson = payloadJson))
            val response = withTimeout(timeoutMillis) {
                deferred.await()
            }
            if (!response.success) {
                throw IpcException(response.error ?: "IPC callback failed without error description")
            }
            return response.payloadJson
        } catch (e: Exception) {
            pendingCallbackResponses.remove(id)
            if (e is IpcException) throw e
            throw IpcException("IPC callback '$callbackType' failed: ${e.message}", e)
        }
    }

    override fun close() {
        readerJob.cancel()
        try {
            input.close()
        } catch (_: Exception) {}
        try {
            output.close()
        } catch (_: Exception) {}
    }
}
