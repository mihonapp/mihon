package mihon.data.extension.model

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.extension.model.ContentWarning
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.serialization.Serializable
import mihon.domain.extension.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkLegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<Source>?,
) {
    @Serializable
    data class Source(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )

    fun toAvailableExtension(store: ExtensionStore, storeBaseUrl: String): Extension.Available {
        return Extension.Available(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = pkg,
            apkUrl = "$storeBaseUrl/apk/$apk",
            iconUrl = "$storeBaseUrl/icon/$pkg.png",
            libVersion = version.substringBeforeLast('.').toDouble(),
            versionCode = code,
            versionName = version,
            lang = lang,
            // The legacy index format has no notion of the three content tiers, so an unset flag
            // only tells us the extension isn't dedicated to adult content, not that it's SAFE.
            contentWarning = if (nsfw == 1) ContentWarning.NSFW else ContentWarning.UNSPECIFIED,
            sources = if (sources.isNullOrEmpty()) {
                listOf(
                    Extension.Available.Source(
                        id = 0,
                        name = name,
                        lang = lang,
                        baseUrl = "",
                    ),
                )
            } else {
                sources.map { source ->
                    Extension.Available.Source(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = source.baseUrl,
                    )
                }
            },
            store = store,
        )
    }
}
