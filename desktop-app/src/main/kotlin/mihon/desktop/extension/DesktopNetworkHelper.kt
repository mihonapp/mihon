package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DesktopNetworkHelper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val defaultUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 MihonW/1.0",
) {
    // Whitelist per package: pkg -> Set of allowed domains
    private val packageWhitelists = ConcurrentHashMap<String, Set<String>>()

    // Global allowed domains across all active extensions: Set of allowed domains
    private val activeWhitelists = ConcurrentHashMap.newKeySet<String>()

    fun registerExtensionDomains(pkg: String, domains: List<String>) {
        val cleanDomains = domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        packageWhitelists[pkg] = cleanDomains
        recomputeActiveWhitelists()
    }

    fun unregisterExtensionDomains(pkg: String) {
        packageWhitelists.remove(pkg)
        recomputeActiveWhitelists()
    }

    private fun recomputeActiveWhitelists() {
        activeWhitelists.clear()
        packageWhitelists.values.forEach { set ->
            activeWhitelists.addAll(set)
        }
    }

    fun isDomainAllowed(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        if (activeWhitelists.isEmpty()) return false

        for (pattern in activeWhitelists) {
            if (matchesDomainPattern(cleanHost, pattern)) {
                return true
            }
        }
        return false
    }

    private fun matchesDomainPattern(host: String, pattern: String): Boolean {
        if (pattern == host) return true
        if (pattern.startsWith("*.")) {
            val root = pattern.removePrefix("*.")
            if (host == root || host.endsWith("." + root)) {
                return true
            }
        }
        return false
    }

    suspend fun downloadRawBytes(request: BrokerHttpRequest): ByteArray = withContext(Dispatchers.IO) {
        val uri = URI(request.url)
        val host = uri.host ?: throw IllegalArgumentException("Target URL has no host: ${request.url}")
        if (!isDomainAllowed(host)) {
            throw SecurityException("Access denied: domain '$host' is not in declared extension domains")
        }

        val builder = Request.Builder().url(request.url)
        var userAgentSet = false
        request.headers.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) {
                userAgentSet = true
            }
            builder.addHeader(k, v)
        }
        if (!userAgentSet) {
            builder.header("User-Agent", defaultUserAgent)
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} downloading ${request.url}")
            }
            response.body.bytes()
        }
    }

    suspend fun executeBrokeredRequest(request: BrokerHttpRequest): BrokerHttpResponse = withContext(Dispatchers.IO) {
        val uri = try {
            URI(request.url)
        } catch (e: Exception) {
            return@withContext BrokerHttpResponse(
                statusCode = 400,
                error = "Invalid target URL: ${e.message}",
            )
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return@withContext BrokerHttpResponse(
                statusCode = 400,
                error = "Target URL has no host: ${request.url}",
            )
        }

        if (!isDomainAllowed(host)) {
            return@withContext BrokerHttpResponse(
                statusCode = 403,
                error = "Access denied: domain '$host' is not in declared extension domains",
            )
        }

        val method = request.method.uppercase()
        val builder = Request.Builder().url(request.url)

        // Headers
        var userAgentSet = false
        request.headers.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) {
                userAgentSet = true
            }
            builder.addHeader(k, v)
        }
        if (!userAgentSet) {
            builder.header("User-Agent", defaultUserAgent)
        }

        // Body
        val contentType = request.headers.entries.firstOrNull {
            it.key.equals("Content-Type", ignoreCase = true)
        }?.value ?: "application/octet-stream"

        val reqBody = request.body
        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post((reqBody ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
            "PUT" -> builder.put((reqBody ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
            "DELETE" -> {
                if (reqBody != null) {
                    builder.delete(reqBody.toRequestBody(contentType.toMediaTypeOrNull()))
                } else {
                    builder.delete()
                }
            }
            else -> builder.method(method, (reqBody ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                val responseHeaders = mutableMapOf<String, String>()
                for (name in response.headers.names()) {
                    responseHeaders[name] = response.headers[name] ?: ""
                }
                val bodyString = response.body.string()

                BrokerHttpResponse(
                    statusCode = response.code,
                    headers = responseHeaders,
                    body = bodyString,
                )
            }
        } catch (e: Exception) {
            BrokerHttpResponse(
                statusCode = 502,
                error = "Brokered HTTP request failed: ${e.message}",
            )
        }
    }
}