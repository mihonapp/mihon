package eu.kanade.tachiyomi.source.online

import java.io.Closeable

/**
 * A local proxy a source runs for the app's WebView opened for it, see [HttpSource.openWebViewProxy]. While the WebView
 * is open, the app sends its requests to [address], accepts the proxy's [certificate] and answers its credentials
 * request, and nothing else. When the WebView is closed, the app stops sending requests there, then calls [close].
 *
 * @since tachiyomix 1.7
 */
interface LocalProxy : Closeable {
    /** Where the proxy listens, as `host:port`. */
    val address: String

    /** The DER encoding of the certificate the proxy presents for the hosts it reads. */
    val certificate: ByteArray

    /** The realm of the proxy's credentials request (HTTP 407). */
    val realm: String

    /** The user name the WebView answers the proxy's credentials request with. */
    val username: String

    /** The password the WebView answers the proxy's credentials request with. */
    val password: String
}
