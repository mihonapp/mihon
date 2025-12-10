package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A marker interface for sources that provide novels.
 */
interface NovelSource {
    suspend fun fetchPageText(page: Page): String
}

/**
 * Checks if this source implements the NovelSource interface.
 * This works across classloaders by checking the interface name.
 */
fun Source.isNovelSource(): Boolean {
    // First try direct instanceof check (works when same classloader)
    if (this is NovelSource) return true

    // Fallback: check by interface name for cross-classloader compatibility
    val directInterfaces = this::class.java.interfaces.map { it.name }
    val allInterfaces = this::class.java.allInterfaces().map { it.name }

    val isNovel = directInterfaces.contains("eu.kanade.tachiyomi.source.NovelSource") ||
        allInterfaces.contains("eu.kanade.tachiyomi.source.NovelSource")

    // Uncomment for debugging
    // println("isNovelSource check for ${this::class.java.name}: direct=$directInterfaces, all=$allInterfaces, result=$isNovel")

    return isNovel
}

/**
 * Calls fetchPageText on a source that implements NovelSource.
 * Uses reflection to handle cross-classloader scenarios.
 */
suspend fun Source.fetchNovelPageText(page: Page): String {
    // First try direct cast (works when same classloader)
    if (this is NovelSource) {
        return this.fetchPageText(page)
    }

    // Fallback: use reflection to call the method
    return suspendCoroutine { continuation ->
        try {
            val method = this::class.java.getMethod(
                "fetchPageText",
                Page::class.java,
                Continuation::class.java,
            )

            @Suppress("UNCHECKED_CAST")
            val result = method.invoke(
                this,
                page,
                object : Continuation<String> {
                    override val context = continuation.context
                    override fun resumeWith(result: Result<String>) {
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    }
                },
            )
            // If the method returns directly (shouldn't happen for suspend), handle it
            if (result != kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED && result is String) {
                continuation.resume(result)
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}

private fun Class<*>.allInterfaces(): List<Class<*>> {
    val result = mutableListOf<Class<*>>()
    var current: Class<*>? = this
    while (current != null) {
        result.addAll(current.interfaces)
        current.interfaces.forEach { result.addAll(it.allInterfaces()) }
        current = current.superclass
    }
    return result
}
