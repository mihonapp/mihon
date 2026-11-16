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

            val hosts = rule.hostsEntries() ?: return addDomain(rule, blockedDomains)
            return hosts.count { !addDomain(it, blockedHosts) } == 0
        }

        private fun addDomain(value: String, domains: MutableSet<String>): Boolean {
            val domain = value.asDomain() ?: return false
            domains.add(domain)
            return true
        }
    }

    companion object {
        val Empty = WebViewAdBlockRules(emptySet(), emptySet(), emptySet())
    }
}

internal fun StringBuilder.appendBraveRules(lines: Sequence<String>) {
    var conditionalDepth = 0
    for (rawLine in lines) {
        val rule = rawLine.trimStart('\uFEFF').trim()
        if (rule.startsWith("!#if ")) conditionalDepth++
        if (rule == "!#endif" && conditionalDepth > 0) conditionalDepth--
        if (conditionalDepth > 0 || rule.isEmpty() || rule.startsWith('!') || rule.startsWith('#') ||
            rule.startsWith("[Adblock", ignoreCase = true)
        ) {
            continue
        }

        val hosts = rule.hostsEntries()
        if (hosts != null) {
            // Hosts entries in saved filters match the exact host, including on URLs with a port.
            for (host in hosts.mapNotNull(String::asDomain)) {
                appendLine("|http://$host^")
                appendLine("|https://$host^")
            }
        } else {
            appendLine(rule.asDomain()?.let { "||$it^" } ?: rule)
        }
    }
}

private fun String.hostsEntries(): List<String>? {
    val separator = indexOfFirst(Char::isWhitespace)
    if (separator < 0 || substring(0, separator) !in HOSTS_ADDRESSES) return null
    return substring(separator).substringBefore('#').trim().split(WHITESPACE)
        .filterNot { it.equals("localhost", ignoreCase = true) }
}

private fun String.asDomain(): String? {
    val domain = trimEnd('.').lowercase()
    return domain.takeIf { it.length <= 253 && DOMAIN.matches(it) }
}

private val HOSTS_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
private val WHITESPACE = Regex("\\s+")
private val DOMAIN = Regex(
    """[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+""",
)
