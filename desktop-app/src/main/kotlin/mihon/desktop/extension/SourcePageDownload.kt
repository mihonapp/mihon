package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.RequestPriority
import mihon.extension.source.model.Page
import mihon.reader.image.ImageFormatDetector
import mihon.reader.image.ReaderImageFormat
import java.io.IOException

/** Shared by online reading and offline downloads: preserve the extension's full image pipeline. */
suspend fun downloadSourcePage(
    sourceId: Long,
    page: Page,
    chapterUrl: String,
    network: DesktopNetworkHelper,
    sourceManager: DesktopSourceManager? = null,
    processManager: WindowsExtensionProcessManager? = null,
    priority: Int = RequestPriority.READER,
): ByteArray = withContext(RequestPriority(priority)) {
    var failure: Exception? = null
    repeat(4) { attempt ->
        try {
            val sourceBytes = when {
                sourceManager != null -> sourceManager.getImage(sourceId, page)
                processManager != null -> processManager.getImage(sourceId, page)
                else -> null
            }
            val bytes = sourceBytes ?: run {
                val imageUrl = page.imageUrl?.takeIf { it.isNotBlank() } ?: page.url
                require(imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
                    "Source returned no absolute image URL"
                }
                network.registerRuntimePageUrl(imageUrl, sourceId = sourceId)
                val headers = page.headers.toMutableMap()
                if (headers.keys.none { it.equals("Referer", ignoreCase = true) } &&
                    (chapterUrl.startsWith("https://") || chapterUrl.startsWith("http://"))
                ) {
                    headers["Referer"] = chapterUrl
                }
                network.downloadRawBytes(
                    BrokerHttpRequest("GET", imageUrl, headers, sourceId = sourceId, priority = priority),
                )
            }
            if (ImageFormatDetector.detect(bytes) == ReaderImageFormat.UNKNOWN) {
                throw mihon.reader.source.ReaderFailure.UnsupportedImage("server returned non-image data")
            }
            return@withContext bytes
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = error
            // Parsing and compatibility errors are deterministic; only retry network failures.
            val message = error.message.orEmpty()
            val retryable = if (error is SourceHttpException) {
                error.code == 408 || error.code == 429 || error.code in 500..599
            } else {
                (error is IOException && error !is mihon.reader.source.ReaderFailure) ||
                    Regex("HTTP(?: error)? (408|429|5[0-9]{2})").containsMatchIn(message)
            }
            if (!retryable || attempt == 3) throw error
            delay((error as? SourceHttpException)?.retryAfterMillis ?: (2_000L shl attempt))
        }
    }
    throw requireNotNull(failure)
}
