package eu.kanade.tachiyomi.data.sync.service

import android.net.Uri
import androidx.core.net.toUri
import tachiyomi.core.common.util.lang.withIOContext
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A one-shot HTTP listener on the loopback interface, used as the OAuth redirect target.
 *
 * Google only accepts a loopback address or a custom scheme for installed apps. The loopback form
 * is what lets a single "desktop" OAuth client work on every device and every build, since it is
 * not tied to a package name or a signing certificate.
 */
class LoopbackRedirectServer : Closeable {

    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_ADDRESS))

    val redirectUri: String = "http://$LOOPBACK_ADDRESS:${serverSocket.localPort}"

    /**
     * Blocks until the browser is redirected here, and returns the authorization code.
     *
     * @throws SyncAuthException if the user denied consent or nothing arrived in time.
     */
    suspend fun awaitAuthorizationCode(timeout: Duration = DEFAULT_TIMEOUT): String = withIOContext {
        serverSocket.soTimeout = timeout.inWholeMilliseconds.toInt()

        val requestUri = try {
            serverSocket.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                val requestLine = reader.readLine()
                    ?: throw SyncAuthException("Empty request on the redirect listener")

                // Answer before parsing so the browser always shows something instead of hanging
                socket.getOutputStream().writer().apply {
                    write(RESPONSE)
                    flush()
                }

                // "GET /?code=... HTTP/1.1" — the path is the only part that carries the result
                val path = requestLine.split(" ").getOrNull(1)
                    ?: throw SyncAuthException("Malformed request on the redirect listener")
                "$redirectUri$path".toUri()
            }
        } catch (e: java.net.SocketTimeoutException) {
            throw SyncAuthException("Timed out waiting for the Google sign-in to complete", e)
        }

        readCode(requestUri)
    }

    private fun readCode(uri: Uri): String {
        uri.getQueryParameter("error")?.let { throw SyncAuthException("Google refused the sign-in: $it") }

        return uri.getQueryParameter("code")
            ?: throw SyncAuthException("Google did not return an authorization code")
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    companion object {
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
        private val DEFAULT_TIMEOUT = 5.minutes

        private val RESPONSE = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<title>Sync</title></head><body style=\"font-family:sans-serif;text-align:center;padding:3em\">")
            append("<h2>You can close this tab</h2><p>Go back to the app to finish setting up sync.</p>")
            append("</body></html>")
        }
    }
}
