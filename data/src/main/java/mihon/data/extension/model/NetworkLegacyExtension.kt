package mihon.data.extension.model

import android.annotation.SuppressLint
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
            isNsfw = nsfw == 1,
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
