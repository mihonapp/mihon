package eu.kanade.tachiyomi.jsplugin.runtime

import android.content.Context
import com.dokar.quickjs.QuickJs
import eu.kanade.tachiyomi.jsplugin.library.JSLibraryProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.Closeable

/**
 * JavaScript plugin runtime using QuickJS.
 * 
 * Based on iReader's architecture:
 * - JSLibraryProvider handles all API setup (fetch, cheerio, storage)
 * - Minimal runtime that just loads and executes plugins
 * - Clean separation between Kotlin logic and JS execution
 */
class PluginRuntime(
    private val pluginId: String,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val libraryProvider = JSLibraryProvider(pluginId)
    
    /**
     * Execute plugin code and return an instance ready to call methods.
     * The QuickJS runtime is bound to the provided dispatcher to ensure
     * JNI environment consistency.
     */
    suspend fun executePlugin(code: String): PluginInstance {
        val cleanCode = sanitizeCode(code)
        val runtime = QuickJs.create(dispatcher)
        
        try {
            // Setup all native bindings and JS runtime via JSLibraryProvider
            libraryProvider.setup(runtime)
            
            logcat(LogPriority.DEBUG) { "[$pluginId] Executing plugin code (${cleanCode.length} chars)" }
            
            // Execute plugin code
            try {
                runtime.evaluate<Any?>(code = cleanCode, filename = "$pluginId.js", asModule = false)
            } catch (evalError: Exception) {
                val errorMsg = evalError.message.orEmpty()
                logcat(LogPriority.ERROR) { "[$pluginId] Plugin code evaluation failed: $errorMsg" }
                // Try to find the error location
                if (errorMsg.contains("SyntaxError")) {
                    // Log more context around the problematic area
                    logcat(LogPriority.ERROR) { "[$pluginId] Full error: $evalError" }
                }
                throw evalError
            }
            
            // Extract plugin instance - LNReader plugins export a class that needs instantiation
            runtime.evaluate<Any?>(
                code = """
                    var PluginClass = exports.default || module.exports.default || module.exports;
                    if (!PluginClass) throw new Error('No plugin exported');
                    // If it's a class/constructor, instantiate it
                    if (typeof PluginClass === 'function') {
                        plugin = new PluginClass();
                    } else {
                        plugin = PluginClass;
                    }
                    console.log('Plugin loaded: ' + (plugin.name || plugin.id || 'unknown'));
                """.trimIndent(),
                filename = "loader.js",
                asModule = false,
            )
            
            return PluginInstance(this, runtime)
        } catch (e: Exception) {
            runtime.close()
            throw e
        }
    }
    
    private fun sanitizeCode(code: String): String {
        var result = code
        // Remove BOM
        if (result.startsWith("\uFEFF")) result = result.substring(1)
        // Remove null characters
        result = result.replace("\u0000", "")
        // Remove other problematic control characters (except newlines and tabs)
        result = result.replace(Regex("[\u0001-\u0008\u000B\u000C\u000E-\u001F]"), "")
        // Normalize line endings
        result = result.replace("\r\n", "\n").replace("\r", "\n")
        // Remove any trailing whitespace per line and overall
        result = result.trim()
        
        // Log code stats for debugging syntax errors
        if (result.isNotEmpty()) {
            val preview = result.take(200).replace("\n", "\\n")
            val openBraces = result.count { it == '{' }
            val closeBraces = result.count { it == '}' }
            val openParens = result.count { it == '(' }
            val closeParens = result.count { it == ')' }
            logcat(LogPriority.DEBUG) { "[$pluginId] Code length=${result.length}, braces={$openBraces/$closeBraces}, parens=($openParens/$closeParens)" }
            logcat(LogPriority.DEBUG) { "[$pluginId] Code preview: $preview..." }
            // Log last 200 chars too
            val suffix = result.takeLast(200).replace("\n", "\\n")
            logcat(LogPriority.DEBUG) { "[$pluginId] Code suffix: ...$suffix" }
        }
        
        return result
    }
    
    /**
     * Wrap plugin code to handle 'this' context issues in transpiled TypeScript.
     * The __awaiter and __generator helpers use 'this' which is undefined at global scope.
     */
    private fun wrapPluginCode(code: String): String {
        // Wrap the code to provide a proper 'this' context
        return """
            (function() {
                var __this = this || globalThis;
                $code
            }).call(globalThis);
        """.trimIndent()
    }
    
    fun cleanup() {
        libraryProvider.cleanup()
    }
}

/**
 * Represents an instantiated plugin ready to execute methods.
 */
class PluginInstance(
    private val runtime: PluginRuntime,
    private val quickJs: QuickJs,
) : Closeable {
    
    suspend fun getId(): String? = getProperty("id") as? String
    suspend fun getName(): String? = getProperty("name") as? String
    suspend fun getVersion(): String? = getProperty("version") as? String
    suspend fun getSite(): String? = getProperty("site") as? String
    
    private suspend fun getProperty(name: String): Any? = quickJs.evaluate<Any?>("plugin.$name")
    
    suspend fun execute(script: String): Any? = quickJs.evaluate<Any?>(script)
    
    override fun close() {
        quickJs.close()
        runtime.cleanup()
    }
}
