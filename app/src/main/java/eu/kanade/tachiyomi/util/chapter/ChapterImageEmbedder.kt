package eu.kanade.tachiyomi.util.chapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.NovelDownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.regex.Pattern

/**
 * Utility class for extracting image URLs from HTML and embedding them as base64.
 */
class ChapterImageEmbedder(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val novelDownloadPreferences: NovelDownloadPreferences = Injekt.get(),
) {
    private val client: OkHttpClient get() = networkHelper.client

    // Regex patterns for finding image URLs in HTML
    private val imgSrcPattern = Pattern.compile(
        """<img[^>]+src\s*=\s*["']([^"']+)["'][^>]*>""",
        Pattern.CASE_INSENSITIVE,
    )

    private val imgSrcsetPattern = Pattern.compile(
        """<img[^>]+srcset\s*=\s*["']([^"']+)["'][^>]*>""",
        Pattern.CASE_INSENSITIVE,
    )

    // Pattern for background-image CSS
    private val bgImagePattern = Pattern.compile(
        """background-image\s*:\s*url\s*\(\s*["']?([^"')]+)["']?\s*\)""",
        Pattern.CASE_INSENSITIVE,
    )

    /**
     * Process HTML content and embed images as base64 if enabled.
     *
     * @param html The HTML content to process
     * @param baseUrl The base URL of the chapter for resolving relative URLs
     * @return Processed HTML with embedded images
     */
    suspend fun processHtml(html: String, baseUrl: String?): String = withContext(Dispatchers.IO) {
        if (!novelDownloadPreferences.downloadChapterImages().get()) {
            return@withContext html
        }

        var processedHtml = html
        val imageUrls = extractImageUrls(html)

        logcat { "ChapterImageEmbedder: Found ${imageUrls.size} images to process" }

        for (imageUrl in imageUrls) {
            try {
                val absoluteUrl = resolveUrl(imageUrl, baseUrl)
                val base64Data = downloadAndEncodeImage(absoluteUrl)

                if (base64Data != null) {
                    // Replace the URL with base64 data URI
                    processedHtml = processedHtml.replace(imageUrl, base64Data)
                    logcat { "ChapterImageEmbedder: Embedded image $imageUrl" }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "ChapterImageEmbedder: Failed to process image $imageUrl" }
            }
        }

        processedHtml
    }

    /**
     * Extract all image URLs from HTML content.
     */
    private fun extractImageUrls(html: String): Set<String> {
        val urls = mutableSetOf<String>()

        // Find img src attributes
        val imgMatcher = imgSrcPattern.matcher(html)
        while (imgMatcher.find()) {
            imgMatcher.group(1)?.let { url ->
                if (!url.startsWith("data:")) {
                    urls.add(url)
                }
            }
        }

        // Find img srcset attributes (take first URL from srcset)
        val srcsetMatcher = imgSrcsetPattern.matcher(html)
        while (srcsetMatcher.find()) {
            srcsetMatcher.group(1)?.let { srcset ->
                // srcset format: "url1 1x, url2 2x, ..." - take the first URL
                val firstUrl = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (firstUrl != null && !firstUrl.startsWith("data:")) {
                    urls.add(firstUrl)
                }
            }
        }

        // Find background-image URLs
        val bgMatcher = bgImagePattern.matcher(html)
        while (bgMatcher.find()) {
            bgMatcher.group(1)?.let { url ->
                if (!url.startsWith("data:")) {
                    urls.add(url)
                }
            }
        }

        return urls
    }

    /**
     * Resolve a potentially relative URL against a base URL.
     */
    private fun resolveUrl(url: String, baseUrl: String?): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") && baseUrl != null -> {
                try {
                    val base = URL(baseUrl)
                    "${base.protocol}://${base.host}$url"
                } catch (e: Exception) {
                    url
                }
            }
            baseUrl != null -> {
                try {
                    URL(URL(baseUrl), url).toString()
                } catch (e: Exception) {
                    url
                }
            }
            else -> url
        }
    }

    /**
     * Download an image and encode it as base64 data URI.
     */
    private suspend fun downloadAndEncodeImage(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                logcat(LogPriority.WARN) { "ChapterImageEmbedder: Failed to download $url - ${response.code}" }
                return@withContext null
            }

            val contentType = response.header("Content-Type") ?: "image/jpeg"
            val mimeType = when {
                contentType.contains("png") -> "image/png"
                contentType.contains("gif") -> "image/gif"
                contentType.contains("webp") -> "image/webp"
                contentType.contains("svg") -> "image/svg+xml"
                else -> "image/jpeg"
            }

            val imageBytes = response.body.bytes()

            // Check if compression is needed
            val maxSizeKb = novelDownloadPreferences.maxImageSizeKb().get()
            val compressionQuality = novelDownloadPreferences.imageCompressionQuality().get()

            val finalBytes = if (maxSizeKb > 0 && imageBytes.size > maxSizeKb * 1024 && mimeType != "image/svg+xml") {
                compressImage(imageBytes, compressionQuality, maxSizeKb)
            } else {
                imageBytes
            }

            val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
            val finalMimeType = if (finalBytes !== imageBytes) "image/jpeg" else mimeType

            "data:$finalMimeType;base64,$base64"
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "ChapterImageEmbedder: Error downloading image $url" }
            null
        }
    }

    /**
     * Compress an image to fit within the size limit.
     */
    private fun compressImage(imageBytes: ByteArray, quality: Int, maxSizeKb: Int): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return imageBytes

            var currentQuality = quality
            var outputBytes: ByteArray

            do {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
                outputBytes = outputStream.toByteArray()
                currentQuality -= 10
            } while (outputBytes.size > maxSizeKb * 1024 && currentQuality > 10)

            bitmap.recycle()
            outputBytes
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "ChapterImageEmbedder: Error compressing image" }
            imageBytes
        }
    }
}
