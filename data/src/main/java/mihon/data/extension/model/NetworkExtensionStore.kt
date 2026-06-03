package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extension.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String,
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: Contact,
    @ProtoNumber(5) val extensions: List<Extension>,
) : BaseNetworkExtensionStore {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String,
        @ProtoNumber(2) val discord: String?,
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val sources: List<Source>,
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val contentRating: ContentRating = ContentRating.SAFE,
        @ProtoNumber(7) val message: String? = null,
    )

    @Suppress("Unused")
    enum class ContentRating {
        @ProtoNumber(0)
        @JsonNames("CONTENT_RATING_SAFE")
        SAFE,

        @ProtoNumber(1)
        @JsonNames("CONTENT_RATING_SUGGESTIVE")
        SUGGESTIVE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_RATING_EROTICA")
        EROTICA,

        @ProtoNumber(3)
        @JsonNames("CONTENT_RATING_PORNOGRAPHIC")
        PORNOGRAPHIC,
    }

    override fun toExtensionStore(indexUrl: String): ExtensionStore {
        return ExtensionStore(
            indexUrl = indexUrl,
            name = name,
            badgeLabel = badgeLabel,
            signingKey = signingKey,
            contact = ExtensionStore.Contact(
                website = contact.website,
                discord = contact.discord,
            ),
            isLegacy = false,
        )
    }

    fun toAvailableExtensions(store: ExtensionStore): List<eu.kanade.tachiyomi.extension.model.Extension.Available> {
        return extensions.map { extension ->
            val lang = extension.sources.map { it.language }.toSet()
            eu.kanade.tachiyomi.extension.model.Extension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                apkUrl = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl,
                libVersion = extension.extensionLib.toDouble(),
                versionCode = extension.versionCode,
                versionName = extension.versionName,
                lang = if (lang.size == 1) lang.first() else "all",
                isNsfw = extension.sources.maxOfOrNull { it.contentRating } == ContentRating.PORNOGRAPHIC,
                sources = extension.sources.map { source ->
                    eu.kanade.tachiyomi.extension.model.Extension.Available.Source(
                        id = source.id,
                        name = source.name,
                        lang = source.language,
                        baseUrl = source.homeUrl,
                    )
                },
                store = store,
            )
        }
    }
}
