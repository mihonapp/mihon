package mihon.domain.network

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Parses the Netscape `cookies.txt` format.
 *
 * Each cookie is one line of seven tab-separated fields:
 * `domain  includeSubdomains  path  secure  expiry  name  value`
 *
 * - `expiry` is in Unix seconds; `0` means a session cookie.
 * - curl marks HttpOnly cookies by prefixing the line with `#HttpOnly_`.
 * - Any other line starting with `#`, and blank lines, are ignored.
 * - The format has no SameSite field, so it is always [SameSite.Unspecified].
 */
object NetscapeCookieParser {

    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"
    private const val FIELD_COUNT = 7

    // 9999-12-31T23:59:59Z, the last second RFC 1123 dates can format (4 digit year)
    private const val MAX_EXPIRY_SECONDS = 253_402_300_799L

    fun parse(text: String, now: Instant = Clock.System.now()): Map<String, List<Cookie>> {
        val cookiesByHost = linkedMapOf<String, MutableList<Cookie>>()

        for (rawLine in text.lineSequence()) {
            var line = rawLine.removeSuffix("\r")
            var httpOnly = false

            if (line.startsWith(HTTP_ONLY_PREFIX)) {
                httpOnly = true
                line = line.removePrefix(HTTP_ONLY_PREFIX)
            } else if (line.isBlank() || line.trimStart().startsWith("#")) {
                continue
            }

            val fields = line.split('\t').takeIf { it.size == FIELD_COUNT } ?: continue

            val host = fields[0].trim().removePrefix(".").lowercase()
            val name = fields[5]
            if (host.isEmpty() || name.isBlank()) continue

            val expirySeconds = fields[4].trim().toLongOrNull() ?: continue
            val expiresAt = if (expirySeconds > 0) {
                Instant.fromEpochSeconds(expirySeconds.coerceAtMost(MAX_EXPIRY_SECONDS))
            } else {
                null
            }
            if (expiresAt != null && expiresAt <= now) continue

            val path = fields[2].trim().let { if (it.startsWith("/")) it else "/$it" }

            cookiesByHost.getOrPut(host) { mutableListOf() } += Cookie(
                name = name,
                value = fields[6],
                path = path,
                hostOnly = !fields[1].trim().equals("TRUE", ignoreCase = true),
                expiresAt = expiresAt,
                secure = fields[3].trim().equals("TRUE", ignoreCase = true),
                httpOnly = httpOnly,
                sameSite = SameSite.Unspecified,
            )
        }

        return cookiesByHost
    }
}
