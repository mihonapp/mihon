@file:OptIn(okhttp3.internal.OkHttpInternalApi::class)

package mihon.extension.host

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.BrokerHttpRequest
import mihon.extension.ipc.BrokerHttpResponse
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.Handshake
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttp
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.connection.RealCall
import okio.Buffer
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** OkHttp 5.5 transport boundary: no socket is opened in the extension process. */
class BrokerTransport(
    private val extensionId: String?,
    private val sourceId: Long?,
    private val execute: suspend (BrokerHttpRequest) -> BrokerHttpResponse,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val client = (chain.call() as? RealCall)?.client ?: throw IOException("Unsupported OkHttp call implementation")
        val position = client.interceptors.indexOfFirst { it === this }
        check(position >= 0)
        val downstream = client.interceptors.drop(position + 1) + client.networkInterceptors
        return TransportChain(chain, downstream, 0, chain.request()).proceed(chain.request())
    }

    private inner class TransportChain(
        private val original: Interceptor.Chain,
        private val interceptors: List<Interceptor>,
        private val index: Int,
        private val requestValue: Request,
        private val connectMs: Int = original.connectTimeoutMillis(),
        private val readMs: Int = original.readTimeoutMillis(),
        private val writeMs: Int = original.writeTimeoutMillis(),
    ) : Interceptor.Chain by original {
        private fun wrap(
            changed: Interceptor.Chain,
        ): Interceptor.Chain = TransportChain(changed, interceptors, index, requestValue, connectMs, readMs, writeMs)
        override fun withDns(dns: Dns) = wrap(original.withDns(dns))
        override fun withSocketFactory(
            socketFactory: javax.net.SocketFactory,
        ) = wrap(original.withSocketFactory(socketFactory))
        override fun withRetryOnConnectionFailure(
            retryOnConnectionFailure: Boolean,
        ) = wrap(original.withRetryOnConnectionFailure(retryOnConnectionFailure))
        override fun withAuthenticator(authenticator: Authenticator) = wrap(original.withAuthenticator(authenticator))
        override fun withCookieJar(cookieJar: CookieJar) = wrap(original.withCookieJar(cookieJar))
        override fun withCache(cache: Cache?) = wrap(original.withCache(cache))
        override fun withProxy(proxy: java.net.Proxy?) = wrap(original.withProxy(proxy))
        override fun withProxySelector(
            proxySelector: java.net.ProxySelector,
        ) = wrap(original.withProxySelector(proxySelector))
        override fun withProxyAuthenticator(
            proxyAuthenticator: Authenticator,
        ) = wrap(original.withProxyAuthenticator(proxyAuthenticator))
        override fun withSslSocketFactory(
            sslSocketFactory: javax.net.ssl.SSLSocketFactory?,
            x509TrustManager: javax.net.ssl.X509TrustManager?,
        ) = wrap(original.withSslSocketFactory(sslSocketFactory, x509TrustManager))
        override fun withHostnameVerifier(
            hostnameVerifier: javax.net.ssl.HostnameVerifier,
        ) = wrap(original.withHostnameVerifier(hostnameVerifier))
        override fun withCertificatePinner(
            certificatePinner: CertificatePinner,
        ) = wrap(original.withCertificatePinner(certificatePinner))
        override fun withConnectionPool(
            connectionPool: ConnectionPool,
        ) = wrap(original.withConnectionPool(connectionPool))
        override fun request() = requestValue
        override fun call() = original.call()
        override fun connection(): Connection? = null
        override fun connectTimeoutMillis() = connectMs
        override fun readTimeoutMillis() = readMs
        override fun writeTimeoutMillis() = writeMs
        override fun withConnectTimeout(
            timeout: Int,
            unit: TimeUnit,
        ): Interceptor.Chain = TransportChain(
            original,
            interceptors,
            index,
            requestValue,
            unit.toMillis(timeout.toLong()).toInt(),
            readMs,
            writeMs,
        )
        override fun withReadTimeout(
            timeout: Int,
            unit: TimeUnit,
        ): Interceptor.Chain = TransportChain(
            original,
            interceptors,
            index,
            requestValue,
            connectMs,
            unit.toMillis(timeout.toLong()).toInt(),
            writeMs,
        )
        override fun withWriteTimeout(
            timeout: Int,
            unit: TimeUnit,
        ): Interceptor.Chain = TransportChain(
            original,
            interceptors,
            index,
            requestValue,
            connectMs,
            readMs,
            unit.toMillis(timeout.toLong()).toInt(),
        )
        override fun proceed(request: Request): Response {
            if (call().isCanceled()) throw IOException("Canceled")
            if (index <
                interceptors.size
            ) {
                return interceptors[index].intercept(
                    TransportChain(original, interceptors, index + 1, request, connectMs, readMs, writeMs),
                )
            }
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val payload =
                BrokerHttpRequest(
                    request.method,
                    request.url.toString(),
                    extensionId = extensionId,
                    sourceId = sourceId,
                    priority = ExtensionExecutionContext.currentPriority(),
                    bodyBase64 = request.body?.let { Base64.getEncoder().encodeToString(buffer.readByteArray()) },
                    headerValues = request.headers.newBuilder().apply {
                        request.body?.contentType()?.let { set("Content-Type", it.toString()) }
                    }.build().toMultimap(),
                )
            val result = try {
                runBlocking {
                    val pending = async { execute(payload) }
                    val monitor =
                        launch {
                            while (isActive) {
                                if (call().isCanceled()) {
                                    pending.cancel()
                                    break
                                }
                                delay(10)
                            }
                        }
                    try {
                        pending.await()
                    } finally {
                        monitor.cancel()
                    }
                }
            } catch (error: CancellationException) {
                throw IOException("Canceled", error)
            }
            if (call().isCanceled()) throw IOException("Canceled")
            if (result.error != null) throw IOException(result.error)
            val headers = Headers.Builder().apply {
                if (result.headerValues.isNotEmpty()) {
                    result.headerValues.forEach { (name, values) -> values.forEach { add(name, it) } }
                } else {
                    result.headers.forEach { (name, value) -> add(name, value) }
                }
            }.build()
            val bytes = result.bodyBase64?.let { Base64.getDecoder().decode(it) } ?: result.body.orEmpty().toByteArray()
            return Response.Builder().request(result.finalUrl?.let { request.newBuilder().url(it).build() } ?: request)
                .protocol(Protocol.HTTP_1_1).code(result.statusCode).message("").headers(headers)
                .body(bytes.toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull())).build()
        }
    }
}
