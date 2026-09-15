package app.cash.quickjs

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.io.Closeable

/** Source-compatible evaluate subset; bytecode and Java bindings are deliberately unsupported. */
class QuickJs private constructor() : Closeable {
    private var closed = false
    private var deadline = 0L
    private val factory = object : ContextFactory() {
        override fun makeContext(): Context = super.makeContext().apply {
            optimizationLevel = -1
            languageVersion = Context.VERSION_ES6
            instructionObserverThreshold = 10_000
            setClassShutter { false }
        }
        override fun observeInstructionCount(context: Context, count: Int) {
            check(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline) {
                "JavaScript execution limit exceeded"
            }
        }
    }
    private val scope: Scriptable = factory.call(ContextAction { it.initSafeStandardObjects() })

    @Synchronized
    @JvmOverloads
    fun evaluate(script: String, fileName: String = "extension.js"): Any? {
        check(!closed) { "QuickJs is closed" }
        require(script.length <= 2_000_000) { "JavaScript input exceeds 2 MB limit" }
        deadline = System.nanoTime() + 2_000_000_000L
        return factory.call(
            ContextAction { context ->
                when (val value = context.evaluateString(scope, script, fileName, 1, null)) {
                    null, is Undefined -> null
                    is CharSequence -> value.toString()
                    is Number, is Boolean -> value
                    else -> throw UnsupportedOperationException(
                        "QuickJs compatibility returns primitive values only; use JSON.stringify",
                    )
                }
            },
        )
    }

    @Synchronized
    override fun close() {
        closed = true
    }

    companion object {
        @JvmStatic fun create(): QuickJs = QuickJs()
    }
}
