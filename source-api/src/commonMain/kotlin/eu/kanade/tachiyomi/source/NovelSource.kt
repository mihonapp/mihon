package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Interface for sources that provide novel (text-based) content.
 * Sources implementing this interface should:
 * - Override [isNovelSource] to return true (done automatically if extending NovelHttpSource)
 * - Implement [fetchPageText] to return the text content for a page
 */
interface NovelSource {
    /**
     * Fetches the text content for a page.
     * Called by HttpPageLoader when the source is identified as a novel source.
     *
     * @param page The page to fetch content for. Use page.url to make the request.
     * @return The HTML or text content to display.
     */
    suspend fun fetchPageText(page: Page): String
}

/**
 * Checks if this source is a novel source.
 * First checks the [Source.isNovelSource] property, then falls back to interface detection.
 */
fun Source.isNovelSource(): Boolean {
    // First check the property (fastest, works for sources that set it)
    if (this.isNovelSource) return true

    // Then try direct instanceof check (works when same classloader)
    if (this is NovelSource) return true

    // Fallback: check by interface name for cross-classloader compatibility
    val allInterfaces = this::class.java.allInterfaces().map { it.name }

    return allInterfaces.contains("eu.kanade.tachiyomi.source.NovelSource")
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
