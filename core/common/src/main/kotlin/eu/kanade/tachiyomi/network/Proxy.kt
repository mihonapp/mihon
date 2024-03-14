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
class Proxy(
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

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is Proxy) return false

        if (proxyType?.name != other.proxyType?.name) return false
        if (host != other.host) return false
        if (port != other.port) return false
        if (username != other.username) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = proxyType?.hashCode() ?: 0
        result = 31 * result + (host?.hashCode() ?: 0)
        result = 31 * result + (port ?: 0)
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        return result
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
