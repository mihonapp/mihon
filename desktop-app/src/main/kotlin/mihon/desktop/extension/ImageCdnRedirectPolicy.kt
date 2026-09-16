package mihon.desktop.extension

import okhttp3.HttpUrl

/** Compatibility for an image service migration, scoped to one already-authorized request. */
internal object ImageCdnRedirectPolicy {
    fun allows(extensionId: String?, method: String, from: HttpUrl, to: HttpUrl): Boolean {
        if (extensionId != "eu.kanade.tachiyomi.extension.all.nhentaixxx" ||
            method !in setOf("GET", "HEAD") ||
            !from.isHttps || !to.isHttps || from.port != 443 || to.port != 443 ||
            from.username.isNotEmpty() || from.password.isNotEmpty() ||
            to.username.isNotEmpty() || to.password.isNotEmpty() ||
            from.encodedPath != to.encodedPath || from.encodedQuery != to.encodedQuery
        ) {
            return false
        }
        // The image service returns 301 from i4.nhentai.xxx to i4.nhentaimg.com.
        // Preserve the shard and asset path; do not authorize arbitrary redirects or future requests.
        val shard = IMAGE_HOST.matchEntire(from.host)?.groupValues?.get(1) ?: return false
        return to.host == "$shard.nhentaimg.com"
    }

    private val IMAGE_HOST = Regex("(i[1-9][0-9]?)\\.nhentai\\.xxx")
}
