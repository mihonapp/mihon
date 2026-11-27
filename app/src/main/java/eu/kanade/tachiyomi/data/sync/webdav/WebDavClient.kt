package eu.kanade.tachiyomi.data.sync.webdav

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WebDavException(
    val statusCode: Int,
    message: String,
) : IOException(message)

data class WebDavRemoteFile(
    val bytes: ByteArray,
    val etag: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WebDavRemoteFile) return false
        return bytes.contentEquals(other.bytes) && etag == other.etag
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (etag?.hashCode() ?: 0)
        return result
    }
}

@SingleIn(AppScope::class)
class WebDavClient internal constructor(
    private val client: OkHttpClient,
) {
    @Inject
    constructor(networkHelper: NetworkHelper) : this(networkHelper.client)

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val baseUrl = parseBaseUrl(config.serverUrl)
        val propfindRequest = Request.Builder()
            .url(baseUrl)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .applyAuth(config)
            .build()

        client.newCall(propfindRequest).execute().use { response ->
            when {
                response.isSuccessful || response.code == 207 -> Unit
                response.code == 405 -> verifyWithOptions(baseUrl, config)
                response.code == 401 || response.code == 403 -> {
                    throw WebDavException(response.code, "Authentication failed (HTTP ${response.code})")
                }
                else -> {
                    throw WebDavException(response.code, "WebDAV server error (HTTP ${response.code})")
                }
            }
        }

        ensureDirectory(config)
    }

    private fun verifyWithOptions(baseUrl: HttpUrl, config: WebDavConfig) {
        val optionsRequest = Request.Builder()
            .url(baseUrl)
            .method("OPTIONS", null)
            .applyAuth(config)
            .build()
        client.newCall(optionsRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException(response.code, "WebDAV server error (HTTP ${response.code})")
            }
        }
    }

    suspend fun ensureDirectory(config: WebDavConfig) = withContext(Dispatchers.IO) {
        val dirUrls = buildDirectoryUrls(config.serverUrl, config.remotePath)
        for (dirUrl in dirUrls) {
            val request = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", EMPTY_BODY)
                .applyAuth(config)
                .build()

            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful ||
                    response.code == 405 || // Collection already exists (RFC 4918)
                    response.code == 301 ||
                    response.code == 302
                if (!ok) {
                    throw WebDavException(
                        response.code,
                        "Failed to create remote directory ${dirUrl.encodedPath} (HTTP ${response.code})",
                    )
                }
            }
        }
    }

    suspend fun headEtag(config: WebDavConfig, fileName: String): String? = withContext(Dispatchers.IO) {
        val fileUrl = buildFileUrl(config.serverUrl, config.remotePath, fileName)
        val request = Request.Builder()
            .url(fileUrl)
            .head()
            .applyAuth(config)
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> extractEtag(response.header("ETag"), response.header("Last-Modified"))
                else -> throw WebDavException(response.code, "HEAD failed (HTTP ${response.code})")
            }
        }
    }

    suspend fun download(config: WebDavConfig, fileName: String): WebDavRemoteFile? = withContext(Dispatchers.IO) {
        val fileUrl = buildFileUrl(config.serverUrl, config.remotePath, fileName)
        val request = Request.Builder()
            .url(fileUrl)
            .get()
            .applyAuth(config)
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.isSuccessful -> WebDavRemoteFile(
                    bytes = response.body.bytes(),
                    etag = extractEtag(response.header("ETag"), response.header("Last-Modified")),
                )
                response.code == 401 || response.code == 403 -> {
                    throw WebDavException(response.code, "Authentication failed (HTTP ${response.code})")
                }
                else -> throw WebDavException(response.code, "Download failed (HTTP ${response.code})")
            }
        }
    }

    suspend fun upload(
        config: WebDavConfig,
        fileName: String,
        bytes: ByteArray,
        contentType: String = "application/gzip",
    ): String? = withContext(Dispatchers.IO) {
        ensureDirectory(config)
        val fileUrl = buildFileUrl(config.serverUrl, config.remotePath, fileName)
        val request = Request.Builder()
            .url(fileUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .applyAuth(config)
            .build()

        val putEtag = client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> extractEtag(response.header("ETag"), response.header("Last-Modified"))
                response.code == 401 || response.code == 403 -> {
                    throw WebDavException(response.code, "Authentication failed (HTTP ${response.code})")
                }
                else -> throw WebDavException(response.code, "Upload failed (HTTP ${response.code})")
            }
        }

        putEtag ?: headEtag(config, fileName)
    }

    private fun Request.Builder.applyAuth(config: WebDavConfig): Request.Builder {
        if (config.username.isNotBlank() || config.password.isNotBlank()) {
            header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        }
        return this
    }

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""

        fun parseBaseUrl(serverUrl: String): HttpUrl {
            val trimmed = serverUrl.trim()
            val url = trimmed.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid WebDAV URL: $trimmed")
            if (url.scheme != "http" && url.scheme != "https") {
                throw IllegalArgumentException("WebDAV URL must use http or https")
            }
            return url
        }

        fun normalizeRemoteSegments(remotePath: String): List<String> {
            return remotePath.split('/')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
        }

        private fun stripTrailingEmptySegments(url: HttpUrl): HttpUrl.Builder {
            val builder = url.newBuilder()
            val segments = url.pathSegments
            var idx = segments.lastIndex
            while (idx >= 0 && segments[idx].isEmpty()) {
                builder.removePathSegment(idx)
                idx--
            }
            return builder
        }

        fun buildDirectoryUrls(serverUrl: String, remotePath: String): List<HttpUrl> {
            val base = parseBaseUrl(serverUrl)
            val segments = normalizeRemoteSegments(remotePath)
            val result = mutableListOf<HttpUrl>()
            val current = stripTrailingEmptySegments(base)
            for (segment in segments) {
                current.addPathSegment(segment)
                result += current.build().newBuilder().addPathSegment("").build()
            }
            return result
        }

        fun buildFileUrl(serverUrl: String, remotePath: String, fileName: String): HttpUrl {
            val builder = stripTrailingEmptySegments(parseBaseUrl(serverUrl))
            for (segment in normalizeRemoteSegments(remotePath)) {
                builder.addPathSegment(segment)
            }
            builder.addPathSegment(fileName)
            return builder.build()
        }

        private fun extractEtag(etag: String?, lastModified: String?): String? {
            return etag?.trim()?.takeIf { it.isNotEmpty() }
                ?: lastModified?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
