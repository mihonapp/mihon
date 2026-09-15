package mihon.extension.host

import android.app.Application
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

/** Identity is restored across coroutine dispatch, and captured Context objects remain private. */
object ExtensionExecutionContext {
    data class Identity(
        val packageId: String?,
        val sourceId: Long?,
        val application: Application,
        val priority: Int = mihon.extension.ipc.RequestPriority.NORMAL,
    )
    private val current = ThreadLocal<Identity?>()
    private val fallback by lazy { Application() }
    init {
        val previous = rx.plugins.RxJavaHooks.getOnScheduleAction()
        rx.plugins.RxJavaHooks.setOnScheduleAction { action ->
            val wrapped = previous?.call(action) ?: action
            val identity = current.get()
            if (identity == null) {
                wrapped
            } else {
                rx.functions.Action0 {
                    duringConstruction(identity) { wrapped.call() }
                }
            }
        }
    }
    fun currentPackageId(): String? = current.get()?.packageId
    fun currentSourceId(): Long? = current.get()?.sourceId
    fun currentPriority(): Int = current.get()?.priority ?: mihon.extension.ipc.RequestPriority.NORMAL
    fun currentApplication(): Application = current.get()?.application ?: fallback
    fun <T> duringConstruction(identity: Identity, block: () -> T): T {
        val previous = current.get()
        current.set(identity)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }
    suspend fun <T> withIdentity(identity: Identity, block: suspend () -> T): T =
        withContext(current.asContextElement(identity)) { block() }
}
