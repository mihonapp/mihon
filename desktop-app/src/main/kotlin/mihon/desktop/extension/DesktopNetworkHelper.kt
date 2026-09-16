package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import mihon.extension.ipc.NetworkFailureKind
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SourceHttpException(
    val code: Int,
    val retryAfterMillis: Long? = null,
    val kind: NetworkFailureKind? = null,
) : IOException("HTTP $code${kind?.let { " · $it" }.orEmpty()}")

class DesktopNetworkHelper(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val defaultUserAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36 MihonW/1.0",
    private val cookieStore: DesktopCookieStore? = null,
    private val scheduler: NetworkRequestScheduler = NetworkRequestScheduler(),
    private val policyProvider: () -> DesktopNetworkPolicy = { DesktopNetworkPolicy() },
) : AutoCloseable {
    // Redirects are followed here so every destination uses its own permissions and session.
    private val baseClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    @Volatile private var client = baseClient
    private var appliedPolicy: DesktopNetworkPolicy? = null

    @Synchronized
    private fun currentClient(): OkHttpClient {
        val policy = policyProvider().validate()
        if (policy != appliedPolicy) {
            val builder = baseClient.newBuilder()
                .connectTimeout(policy.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(policy.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(policy.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            when (policy.proxyMode) {
                DesktopProxyMode.SYSTEM -> builder.proxy(null)
                DesktopProxyMode.DIRECT -> builder.proxy(java.net.Proxy.NO_PROXY)
                DesktopProxyMode.HTTP, DesktopProxyMode.SOCKS -> builder.proxy(
                    java.net.Proxy(
                        if (policy.proxyMode == DesktopProxyMode.HTTP) {
                            java.net.Proxy.Type.HTTP
                        } else {
                            java.net.Proxy.Type.SOCKS
                        },
                        java.net.InetSocketAddress.createUnresolved(policy.proxyHost, policy.proxyPort),
                    ),
                )
            }
            client.connectionPool.evictAll()
            client = builder.build()
            appliedPolicy = policy
        }
        return client
    }
    private val packageWhitelists = ConcurrentHashMap<String, Set<String>>()
    private val runtimeHosts = ConcurrentHashMap<String, MutableSet<String>>()
    private val sourceOwners = ConcurrentHashMap<Long, String>()
    private val memoryCookies = ConcurrentHashMap<String, List<Cookie>>()

    fun userAgentFor(url: String): String = cookieStore?.getUserAgent(url.toHttpUrl().host)
        ?: policyProvider().userAgent.takeIf { it.isNotBlank() } ?: defaultUserAgent

    fun registerExtensionDomains(pkg: String, domains: List<String>) {
        packageWhitelists[pkg] = domains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    fun registerSourceOwner(sourceId: Long, pkg: String) {
        sourceOwners[sourceId] = pkg
    }

    fun unregisterExtensionDomains(pkg: String) {
        packageWhitelists.remove(pkg)
        runtimeHosts.remove(pkg)
        sourceOwners.entries.removeIf { it.value == pkg }
    }

    fun registerRuntimePageUrl(url: String, extensionId: String? = null, sourceId: Long? = null) {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: return
        val owner = extensionId ?: sourceId?.let(sourceOwners::get) ?: LEGACY_OWNER
        runtimeHosts.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(parsed.host)
    }

    fun isDomainAllowed(host: String, extensionId: String? = null): Boolean {
        val cleanHost = host.trim().lowercase()
        val domains = if (extensionId == null) {
            packageWhitelists.values.flatten()
        } else {
            packageWhitelists[extensionId].orEmpty()
        }
        val dynamic = runtimeHosts[extensionId ?: LEGACY_OWNER].orEmpty()
        return cleanHost in dynamic || domains.any { pattern ->
            pattern == "*" || pattern == "*.*" || pattern == cleanHost ||
                (
                    pattern.startsWith("*.") &&
                        (cleanHost == pattern.substring(2) || cleanHost.endsWith(".${pattern.substring(2)}"))
                    )
        }
    }

    suspend fun downloadRawBytes(request: BrokerHttpRequest): ByteArray {
        val response = execute(request)
        if (response.code !in 200..299) {
            throw SourceHttpException(
                response.code,
                retryAfter(
                    response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.firstOrNull(),
                ),
                response.failureKind(),
            )
        }
        return response.bytes
    }

    suspend fun executeBrokeredRequest(request: BrokerHttpRequest): BrokerHttpResponse = try {
        val response = execute(request)
        BrokerHttpResponse(
            statusCode = response.code,
            headers = response.headers.mapValues { it.value.joinToString(", ") },
            body = response.bytes.takeIf { it.size <= MAX_INLINE_BYTES }?.toString(Charsets.UTF_8),
            bodyBase64 = Base64.getEncoder().encodeToString(response.bytes),
            headerValues = response.headers,
            finalUrl = response.url,
            failureKind = response.failureKind(),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (denied: SecurityException) {
        BrokerHttpResponse(403, error = denied.message, failureKind = NetworkFailureKind.DOMAIN_DENIED)
    } catch (invalid: IllegalArgumentException) {
        BrokerHttpResponse(400, error = invalid.message, failureKind = NetworkFailureKind.INVALID_REQUEST)
    } catch (failure: IOException) {
        val kind = when (failure) {
            is javax.net.ssl.SSLException -> NetworkFailureKind.TLS
            is java.net.SocketTimeoutException -> NetworkFailureKind.TIMEOUT
            is java.net.UnknownHostException, is java.net.NoRouteToHostException -> NetworkFailureKind.OFFLINE
            is java.net.ConnectException ->
                if (policyProvider().proxyMode in setOf(DesktopProxyMode.HTTP, DesktopProxyMode.SOCKS)) {
                    NetworkFailureKind.PROXY
                } else {
                    NetworkFailureKind.CONNECTION
                }
            else -> NetworkFailureKind.CONNECTION
        }
        BrokerHttpResponse(502, error = "$kind: ${failure.message}", failureKind = kind)
    }

    private suspend fun execute(request: BrokerHttpRequest): NetworkResponse {
        val owner = request.extensionId ?: request.sourceId?.let(sourceOwners::get)
        val identity = owner ?: LEGACY_OWNER
        var url = request.url.toHttpUrl()
        var method = request.method.uppercase()
        var bytes = request.bodyBase64?.let { Base64.getDecoder().decode(it) } ?: request.body?.toByteArray()
        require((bytes?.size ?: 0) <= MAX_RESPONSE_BYTES) { "Request body exceeds 64 MiB" }
        val headers = linkedMapOf<String, List<String>>()
        request.headers.forEach { (key, value) -> headers[key] = listOf(value) }
        request.headerValues.forEach { (key, values) ->
            headers.keys.removeAll { it.equals(key, true) }
            headers[key] = values
        }
        repeat(MAX_REDIRECTS + 1) { hop ->
            if (!isDomainAllowed(url.host, owner)) {
                throw SecurityException(
                    "Access denied: domain '${url.host}' is not declared for ${owner ?: "the request"}",
                )
            }
            val builder = Request.Builder().url(url)
            headers.forEach { (key, values) -> values.forEach { builder.addHeader(key, it) } }
            if (headers.keys.none { it.equals("User-Agent", true) }) {
                builder.header("User-Agent", userAgentFor(url.toString()))
            }
            if (headers.keys.none { it.equals("Cookie", true) }) {
                val stored = cookieStore?.loadForRequest(identity, url)
                    ?: memoryCookies[identity].orEmpty().filter {
                        it.expiresAt > System.currentTimeMillis() && it.matches(url)
                    }
                val selected = stored.joinToString("; ") { "${it.name}=${it.value}" }
                // User-entered cookies have no Secure metadata and are restricted to HTTPS.
                val manual = if (url.isHttps) cookieStore?.getCookieHeader(url.host) else null
                val value = listOfNotNull(selected.takeIf { it.isNotBlank() }, manual).joinToString("; ")
                if (value.isNotBlank()) builder.header("Cookie", value)
            }
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
            val body = when (method) {
                "GET", "HEAD" -> null
                "POST", "PUT", "PATCH" -> (bytes ?: ByteArray(0)).toRequestBody(contentType?.toMediaTypeOrNull())
                else -> bytes?.toRequestBody(contentType?.toMediaTypeOrNull())
            }
            builder.method(method, body)
            val response = scheduler.withPermit(url.host, "$identity:${request.sourceId}", request.priority) {
                awaitResponse(builder.build(), identity).also { received ->
                    if (received.code == 429 || received.code == 503) {
                        retryAfter(
                            received.headers.entries.firstOrNull {
                                it.key.equals("Retry-After", true)
                            }?.value?.firstOrNull(),
                        )?.let { scheduler.deferHost(url.host, it) }
                    }
                }
            }
            val location = response.headers.entries
                .firstOrNull { it.key.equals("Location", true) }?.value?.firstOrNull()
            if (response.code !in REDIRECT_CODES || location == null) return response
            if (hop == MAX_REDIRECTS) throw IOException("Too many HTTP redirects")
            val next = url.resolve(location) ?: throw IOException("Invalid HTTP redirect")
            if (next.host != url.host || next.scheme != url.scheme || next.port != url.port) {
                headers.keys.removeAll {
                    it.equals("Authorization", true) || it.equals("Cookie", true) || it.equals("Host", true)
                }
            }
            if ((response.code in setOf(301, 302) && method == "POST") || (response.code == 303 && method != "HEAD")) {
                method = "GET"
                bytes = null
                headers.keys.removeAll {
                    it.equals("Content-Type", true) || it.equals("Content-Length", true) ||
                        it.equals("Transfer-Encoding", true)
                }
            }
            url = next
        }
        error("Unreachable redirect state")
    }

    private suspend fun awaitResponse(
        request: Request,
        identity: String,
    ): NetworkResponse = suspendCancellableCoroutine { continuation ->
        val call = currentClient().newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val parsed = Cookie.parseAll(it.request.url, it.headers)
                        if (cookieStore != null) {
                            cookieStore.saveFromResponse(identity, it.request.url, parsed)
                        } else {
                            memoryCookies.compute(identity) { _, old ->
                                old.orEmpty().filter { cookie ->
                                    cookie.expiresAt > System.currentTimeMillis() && parsed.none { fresh ->
                                        fresh.name == cookie.name && fresh.domain == cookie.domain &&
                                            fresh.path == cookie.path
                                    }
                                } + parsed.filter { cookie -> cookie.expiresAt > System.currentTimeMillis() }
                            }
                        }
                        val content = it.body.byteStream().use { stream -> stream.readNBytes(MAX_RESPONSE_BYTES + 1) }
                        if (content.size > MAX_RESPONSE_BYTES) throw IOException("HTTP response exceeds 64 MiB")
                        NetworkResponse(it.code, it.headers.toMultimap(), content, it.request.url.toString())
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        })
    }

    private data class NetworkResponse(
        val code: Int,
        val headers: Map<String, List<String>>,
        val bytes: ByteArray,
        val url: String,
    ) {
        fun failureKind(): NetworkFailureKind? {
            val preview = bytes.take(8_192).toByteArray().toString(Charsets.UTF_8).lowercase()
            return when {
                code == 429 -> NetworkFailureKind.RATE_LIMITED
                code == 403 && "cloudflare" in preview &&
                    ("sorry, you have been blocked" in preview || "error code: 1020" in preview) ->
                    NetworkFailureKind.SITE_BLOCKED
                code in setOf(403, 503) &&
                    (
                        headers.any { (key, values) -> key.equals("cf-mitigated", true) && "challenge" in values } ||
                            "cf-chl-" in preview || "challenge-platform" in preview || "captcha" in preview
                        ) ->
                    NetworkFailureKind.WEB_VERIFICATION
                code == 401 || code == 403 -> NetworkFailureKind.AUTHENTICATION_REQUIRED
                code !in 200..399 -> NetworkFailureKind.HTTP_ERROR
                else -> null
            }
        }
    }

    override fun close() {
        scheduler.close()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val LEGACY_OWNER = "__desktop__"
        private const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
        private const val MAX_INLINE_BYTES = 4 * 1024 * 1024
        private const val MAX_REDIRECTS = 8
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        internal fun retryAfter(raw: String?): Long? {
            raw ?: return null
            raw.trim().toLongOrNull()?.let { return it.coerceIn(0, 86400) * 1000 }
            return runCatching {
                val time = java.time.ZonedDateTime.parse(raw, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli()
                (time - System.currentTimeMillis()).coerceIn(0, 86400000)
            }.getOrNull()
        }
    }
}
