package mihon.extension.host

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
class DesktopMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int get() = if (System.getProperty("mihon.extension.host") ==
        "true"
    ) {
        100
    } else {
        Int.MIN_VALUE
    }
    override fun hintOnError(): String = "Desktop extension main-loop dispatcher"
    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
        if (System.getProperty("mihon.extension.host") != "true") {
            return object : MainCoroutineDispatcher() {
                override val immediate: MainCoroutineDispatcher get() = this
                override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                    !javax.swing.SwingUtilities.isEventDispatchThread()
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    javax.swing.SwingUtilities.invokeLater(block)
                }
            }
        }
        return object : MainCoroutineDispatcher() {
            private val handler = Handler(Looper.getMainLooper())
            override val immediate: MainCoroutineDispatcher get() = this
            override fun isDispatchNeeded(
                context: CoroutineContext,
            ): Boolean = !Thread.currentThread().name.startsWith("android-main-compat")
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                handler.post(block)
            }
        }
    }
}
