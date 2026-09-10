package android.net

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

open class Uri private constructor(private val uriString: String) {

    private val javaUri: java.net.URI? = try {
        java.net.URI(uriString)
    } catch (_: Exception) {
        null
    }

    open val scheme: String? get() = javaUri?.scheme

    open val authority: String? get() = javaUri?.rawAuthority

    open val host: String? get() = javaUri?.host

    open val port: Int get() = javaUri?.port ?: -1

    open val path: String? get() = javaUri?.rawPath

    open val query: String? get() = javaUri?.rawQuery

    open val fragment: String? get() = javaUri?.rawFragment

    open val pathSegments: List<String>
        get() = path?.split('/')?.filter { it.isNotEmpty() }?.map { decode(it) } ?: emptyList()

    open val lastPathSegment: String? get() = pathSegments.lastOrNull()

    open val queryParameterNames: Set<String>
        get() {
            val q = query ?: return emptySet()
            return q.split('&').mapNotNull { param ->
                val eq = param.indexOf('=')
                val rawKey = if (eq >= 0) param.substring(0, eq) else param
                if (rawKey.isNotEmpty()) decode(rawKey) else null
            }.toSet()
        }

    open fun getQueryParameter(key: String): String? {
        val q = query ?: return null
        for (param in q.split('&')) {
            val eq = param.indexOf('=')
            val rawKey = if (eq >= 0) param.substring(0, eq) else param
            if (decode(rawKey) == key) {
                return if (eq >= 0) decode(param.substring(eq + 1)) else ""
            }
        }
        return null
    }

    open fun getQueryParameters(key: String): List<String> {
        val q = query ?: return emptyList()
        val results = mutableListOf<String>()
        for (param in q.split('&')) {
            val eq = param.indexOf('=')
            val rawKey = if (eq >= 0) param.substring(0, eq) else param
            if (decode(rawKey) == key) {
                results.add(if (eq >= 0) decode(param.substring(eq + 1)) else "")
            }
        }
        return results
    }

    open fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val flag = getQueryParameter(key) ?: return defaultValue
        val lower = flag.lowercase()
        return "false" != lower && "0" != lower
    }

    open fun isHierarchical(): Boolean = true
    open fun isRelative(): Boolean =
        javaUri?.isAbsolute?.not() ?: (!uriString.startsWith("http://") && !uriString.startsWith("https://"))
    open fun isAbsolute(): Boolean = !isRelative()

    open fun buildUpon(): Builder = Builder(this)

    override fun toString(): String = uriString

    override fun equals(other: Any?): Boolean = other is Uri && uriString == other.uriString
    override fun hashCode(): Int = uriString.hashCode()

    class Builder() {
        private var scheme: String? = null
        private var authority: String? = null
        private var path: String? = null
        private val queryParams = mutableListOf<Pair<String, String?>>()
        private var rawQuery: String? = null
        private var fragment: String? = null

        internal constructor(uri: Uri) : this() {
            scheme = uri.scheme
            authority = uri.authority
            path = uri.path
            val q = uri.query
            if (q != null) {
                for (param in q.split('&')) {
                    val eq = param.indexOf('=')
                    if (eq >= 0) {
                        queryParams.add(decode(param.substring(0, eq)) to decode(param.substring(eq + 1)))
                    } else if (param.isNotEmpty()) {
                        queryParams.add(decode(param) to null)
                    }
                }
            }
            fragment = uri.fragment
        }

        fun scheme(scheme: String?): Builder = apply { this.scheme = scheme }
        fun authority(authority: String?): Builder = apply { this.authority = authority }
        fun path(path: String?): Builder = apply { this.path = path }

        fun appendPath(newSegment: String): Builder = apply {
            val current = path ?: ""
            val sep = if (current.endsWith("/") || newSegment.startsWith("/")) "" else "/"
            this.path = "$current$sep${encode(newSegment)}"
        }

        fun appendEncodedPath(newSegment: String): Builder = apply {
            val current = path ?: ""
            val sep = if (current.endsWith("/") || newSegment.startsWith("/")) "" else "/"
            this.path = "$current$sep$newSegment"
        }

        fun query(query: String?): Builder = apply {
            this.rawQuery = query
            this.queryParams.clear()
        }

        fun clearQuery(): Builder = apply {
            this.rawQuery = null
            this.queryParams.clear()
        }

        fun appendQueryParameter(key: String, value: String?): Builder = apply {
            queryParams.add(key to value)
            rawQuery = null
        }

        fun fragment(fragment: String?): Builder = apply { this.fragment = fragment }

        fun build(): Uri {
            val sb = StringBuilder()
            if (scheme != null) {
                sb.append(scheme).append("://")
            }
            if (authority != null) {
                sb.append(authority)
            }
            if (path != null) {
                if (authority != null && !path!!.startsWith("/")) sb.append("/")
                sb.append(path)
            }
            val effectiveQuery = when {
                rawQuery != null -> rawQuery
                queryParams.isNotEmpty() -> queryParams.joinToString("&") { (k, v) ->
                    if (v != null) "${encode(k)}=${encode(v)}" else encode(k)
                }
                else -> null
            }
            if (effectiveQuery != null) {
                sb.append("?").append(effectiveQuery)
            }
            if (fragment != null) {
                sb.append("#").append(fragment)
            }
            return Uri(sb.toString())
        }

        override fun toString(): String = build().toString()
    }

    companion object {
        @JvmField
        val EMPTY: Uri = Uri("")

        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri {
            val sb = StringBuilder().append(scheme).append(":").append(ssp)
            if (fragment != null) sb.append("#").append(fragment)
            return Uri(sb.toString())
        }

        private fun decode(s: String): String = try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }

        private fun encode(s: String): String = try {
            URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }
    }
}
