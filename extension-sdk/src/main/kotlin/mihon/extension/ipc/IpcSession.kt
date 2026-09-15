package mihon.extension.ipc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class IpcException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
    override val networkFailure: NetworkFailure? = null,
) : RuntimeException(message, cause), NetworkFailureProvider

class IpcSession(
    private val input: InputStream,
    private val output: OutputStream,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onRequest: (suspend (IpcRequest) -> IpcResponse)? = null,
    private val onCallback: (suspend (IpcCallbackRequest) -> IpcCallbackResponse)? = null,
) : Closeable {
    private val sessionJob = SupervisorJob(scope.coroutineContext[Job])
    private val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
    private val closed = AtomicBoolean()
    private val nextRequestId = AtomicLong(1L)
    private val writeMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<Long, CompletableDeferred<IpcResponse>>()
    private val pendingCallbackResponses = ConcurrentHashMap<Long, CompletableDeferred<IpcCallbackResponse>>()
    private val activeRequests = ConcurrentHashMap<Long, Job>()
    private val activeCallbacks = ConcurrentHashMap<Long, Job>()

    private val readerJob = sessionScope.launch(Dispatchers.IO) {
        try {
            while (isActive) {
                when (val message = IpcFrameCodec.readMessage(input) ?: break) {
                    is IpcResponse -> pendingResponses.remove(message.requestId)?.complete(message)
                    is IpcCallbackResponse -> pendingCallbackResponses.remove(message.requestId)?.complete(message)
                    is IpcCancel -> (if (message.callback) activeCallbacks else activeRequests)
                        .remove(message.requestId)?.cancel()
                    is IpcRequest -> dispatch(activeRequests, message.requestId) {
                        val response = try {
                            onRequest?.invoke(message)
                                ?: IpcResponse(message.requestId, false, error = "No request handler registered")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            IpcResponse(
                                message.requestId,
                                false,
                                error = error.message ?: error::class.java.simpleName,
                                networkFailure = error.findNetworkFailure(),
                            )
                        }
                        writeMessage(response)
                    }
                    is IpcCallbackRequest -> dispatch(activeCallbacks, message.requestId) {
                        val response = try {
                            onCallback?.invoke(message)
                                ?: IpcCallbackResponse(
                                    message.requestId,
                                    false,
                                    error = "No callback handler registered",
                                )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            IpcCallbackResponse(
                                message.requestId,
                                false,
                                error =
                                error.message ?: error::class.java.simpleName,
                            )
                        }
                        writeMessage(response)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Broken pipes and invalid frames terminate this session and its work.
        } finally {
            terminate()
        }
    }

    private fun dispatch(registry: ConcurrentHashMap<Long, Job>, id: Long, block: suspend () -> Unit) {
        val task = sessionScope.launch(start = CoroutineStart.LAZY) { block() }
        registry.put(id, task)?.cancel()
        task.invokeOnCompletion { registry.remove(id, task) }
        task.start()
    }

    private suspend fun writeMessage(message: IpcMessage) {
        if (closed.get()) throw IpcException("IPC connection terminated")
        writeMutex.withLock {
            currentCoroutineContext().ensureActive()
            IpcFrameCodec.writeMessage(output, message)
        }
    }

    private fun cancelRemote(id: Long, callback: Boolean) {
        if (!closed.get()) sessionScope.launch { runCatching { writeMessage(IpcCancel(id, callback)) } }
    }

    suspend fun sendRequest(command: String, payloadJson: String = "", timeoutMillis: Long = 30_000L): String {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<IpcResponse>()
        pendingResponses[id] = deferred
        try {
            writeMessage(
                IpcRequest(
                    id,
                    command,
                    payloadJson,
                    currentCoroutineContext()[RequestPriority]?.value ?: RequestPriority.NORMAL,
                ),
            )
            val response = withTimeout(timeoutMillis) { deferred.await() }
            if (!response.success) {
                throw IpcException(
                    response.error ?: "IPC request failed without error description",
                    networkFailure = response.networkFailure,
                )
            }
            return response.payloadJson
        } catch (cancelled: CancellationException) {
            cancelRemote(id, callback = false)
            currentCoroutineContext().ensureActive()
            throw IpcException("IPC request '$command' timed out", cancelled)
        } catch (error: Exception) {
            if (error is IpcException) throw error
            throw IpcException("IPC request '$command' failed: ${error.message}", error)
        } finally {
            pendingResponses.remove(id)
        }
    }

    suspend fun sendCallback(callbackType: String, payloadJson: String = "", timeoutMillis: Long = 30_000L): String {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<IpcCallbackResponse>()
        pendingCallbackResponses[id] = deferred
        try {
            writeMessage(IpcCallbackRequest(id, callbackType, payloadJson))
            val response = withTimeout(timeoutMillis) { deferred.await() }
            if (!response.success) throw IpcException(response.error ?: "IPC callback failed without error description")
            return response.payloadJson
        } catch (cancelled: CancellationException) {
            cancelRemote(id, callback = true)
            currentCoroutineContext().ensureActive()
            throw IpcException("IPC callback '$callbackType' timed out", cancelled)
        } catch (error: Exception) {
            if (error is IpcException) throw error
            throw IpcException("IPC callback '$callbackType' failed: ${error.message}", error)
        } finally {
            pendingCallbackResponses.remove(id)
        }
    }

    suspend fun awaitTermination() = readerJob.join()

    private fun terminate() {
        if (!closed.compareAndSet(false, true)) return
        val error = IpcException("IPC connection terminated")
        pendingResponses.values.forEach { it.completeExceptionally(error) }
        pendingResponses.clear()
        pendingCallbackResponses.values.forEach { it.completeExceptionally(error) }
        pendingCallbackResponses.clear()
        activeRequests.values.forEach { it.cancel() }
        activeCallbacks.values.forEach { it.cancel() }
        activeRequests.clear()
        activeCallbacks.clear()
        sessionJob.cancel()
        runCatching { input.close() }
        runCatching { output.close() }
    }

    override fun close() = terminate()
}
