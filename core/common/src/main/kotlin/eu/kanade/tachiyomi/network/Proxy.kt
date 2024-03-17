package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.serialization.Serializable
import logcat.LogPriority
import okhttp3.Authenticator
import okhttp3.Credentials
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy.Type
import java.net.UnknownHostException
import java.net.Proxy as JavaProxy

@Serializable
data class Proxy(
    var proxyType: Type? = null,
    var host: String? = null,
    var port: Int? = null,
    var username: String? = null,
    var password: String? = null,
) {
    fun getProxy(): JavaProxy? {
        return if (host == null || port == null) {
            null
        } else {
            JavaProxy(proxyType, InetSocketAddress(InetAddress.getByName(host), port!!))
        }
    }

    fun getAuthenticator(): Authenticator? {
        if (username?.isBlank() == true && password?.isBlank() == true) return null
        return Authenticator { _, response ->
            val credential: String = Credentials.basic(username ?: "", password ?: "")
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }

    companion object {
        suspend fun testHostValidity(host: String): Boolean = withIOContext {
            return@withIOContext try {
                InetAddress.getByName(host)
                true
            } catch (e: UnknownHostException) {
                logcat(LogPriority.WARN, e)
                false
            }
        }

        /**
         * Returns a proxy that is sure to never connect. This blocks the clients network access.
         */
        fun getBlackHoleProxy(context: Context): JavaProxy {
            context.toast(MR.strings.proxy_host_invalid_warning)
            return JavaProxy(Type.SOCKS, InetSocketAddress(Inet6Address.getByName("100::"), 1))
        }
    }
}
