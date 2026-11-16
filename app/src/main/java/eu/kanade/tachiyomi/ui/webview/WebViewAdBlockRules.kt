package eu.kanade.tachiyomi.ui.webview

internal class WebViewAdBlockRules private constructor(
    private val blockedDomains: Set<String>,
    private val blockedHosts: Set<String>,
    private val allowedDomains: Set<String>,
) {
    fun blocks(host: String?, isMainFrame: Boolean): Boolean {
        if (isMainFrame || host == null) return false
        val normalizedHost = host.trimEnd('.').lowercase()
        return (normalizedHost in blockedHosts || normalizedHost.matchesDomain(blockedDomains)) &&
            !normalizedHost.matchesDomain(allowedDomains)
    }

    private fun String.matchesDomain(domains: Set<String>): Boolean {
        if (domains.isEmpty()) return false
        var candidate = this
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    class Builder {
        private val blockedDomains = mutableSetOf<String>()
        private val blockedHosts = mutableSetOf<String>()
        private val allowedDomains = mutableSetOf<String>()

        val hasRules: Boolean
            get() = blockedDomains.isNotEmpty() || blockedHosts.isNotEmpty() || allowedDomains.isNotEmpty()

        fun addRules(lines: Sequence<String>): Int = lines.count { !addRule(it) }

        fun build(): WebViewAdBlockRules =
            WebViewAdBlockRules(blockedDomains.toSet(), blockedHosts.toSet(), allowedDomains.toSet())

        private fun addRule(line: String): Boolean {
            val rule = line.trimStart('\uFEFF').trim()
            if (rule.isEmpty() || rule.startsWith('!') || rule.startsWith('#') ||
                rule.startsWith("[Adblock", ignoreCase = true)
            ) {
                return true
            }
            if (rule.startsWith("@@||") && rule.endsWith('^')) {
                return addDomain(rule.substring(4, rule.lastIndex), allowedDomains)
            }
            if (rule.startsWith("||") && rule.endsWith('^')) {
                return addDomain(rule.substring(2, rule.lastIndex), blockedDomains)
            }

            val separator = rule.indexOfFirst { it.isWhitespace() }
            if (separator < 0) return addDomain(rule, blockedDomains)
            if (rule.substring(0, separator) !in HOSTS_ADDRESSES) return false
            val hosts = rule.substring(separator).substringBefore('#').trim().split(WHITESPACE)
            return hosts.count { !it.equals("localhost", ignoreCase = true) && !addDomain(it, blockedHosts) } == 0
        }

        private fun addDomain(value: String, domains: MutableSet<String>): Boolean {
            val domain = value.trimEnd('.').lowercase()
            if (domain.length > 253 || !DOMAIN.matches(domain)) return false
            domains.add(domain)
            return true
        }
    }

    companion object {
        val Empty = WebViewAdBlockRules(emptySet(), emptySet(), emptySet())

        private val HOSTS_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
        private val WHITESPACE = Regex("\\s+")
        private val DOMAIN = Regex(
            """[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+""",
        )
    }
}
