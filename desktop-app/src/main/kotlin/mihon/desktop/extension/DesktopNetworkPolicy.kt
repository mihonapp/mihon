package mihon.desktop.extension

import mihon.desktop.preferences.DesktopPreferenceStore

enum class DesktopProxyMode { SYSTEM, DIRECT, HTTP, SOCKS }

data class DesktopNetworkPolicy(
    val proxyMode: DesktopProxyMode = DesktopProxyMode.SYSTEM,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 30,
    val userAgent: String = "",
) {
    fun validate(): DesktopNetworkPolicy = apply {
        require(connectTimeoutSeconds in 1..120 && readTimeoutSeconds in 1..300) { "Invalid network timeout" }
        if (proxyMode == DesktopProxyMode.HTTP || proxyMode == DesktopProxyMode.SOCKS) {
            require(
                proxyHost.isNotBlank() && proxyHost.none {
                    it.isWhitespace() || it in "/@"
                },
            ) { "Enter a proxy hostname or IP address" }
            require(proxyPort in 1..65535) { "Proxy port must be between 1 and 65535" }
        }
        require(userAgent.none { it == '\r' || it == '\n' }) { "User-Agent must be one line" }
    }
}

class DesktopNetworkSettingsStore(private val preferences: DesktopPreferenceStore) {
    fun load(): DesktopNetworkPolicy {
        val fields = preferences.propertiesWithPrefix("network.")
        return DesktopNetworkPolicy(
            proxyMode =
            fields["network.proxy_mode"]?.let { runCatching { DesktopProxyMode.valueOf(it) }.getOrNull() }
                ?: DesktopProxyMode.SYSTEM,
            proxyHost = fields["network.proxy_host"].orEmpty(),
            proxyPort = fields["network.proxy_port"]?.toIntOrNull() ?: 8080,
            connectTimeoutSeconds = fields["network.connect_timeout_seconds"]?.toIntOrNull() ?: 15,
            readTimeoutSeconds = fields["network.read_timeout_seconds"]?.toIntOrNull() ?: 30,
            userAgent = fields["network.user_agent"].orEmpty(),
        )
    }

    fun save(policy: DesktopNetworkPolicy) {
        policy.validate()
        preferences.update {
            setProperty("network.proxy_mode", policy.proxyMode.name)
            setProperty("network.proxy_host", policy.proxyHost.trim())
            setProperty("network.proxy_port", policy.proxyPort.toString())
            setProperty("network.connect_timeout_seconds", policy.connectTimeoutSeconds.toString())
            setProperty("network.read_timeout_seconds", policy.readTimeoutSeconds.toString())
            setProperty("network.user_agent", policy.userAgent.trim())
        }
    }
}
