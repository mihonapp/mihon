package mihon.data.extension.model

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.domain.extension.model.ExtensionStore

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class NetworkLegacyExtensionRepo(
    @SerialName("index_v2")
    val indexV2: String?,
    val meta: Meta,
) : BaseNetworkExtensionStore {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String?,
        val website: String,
        val signingKeyFingerprint: String,
    )

    override fun toExtensionStore(indexUrl: String): ExtensionStore {
        return ExtensionStore(
            indexUrl = indexUrl,
            name = meta.name,
            badgeLabel = meta.shortName ?: meta.name,
            signingKey = meta.signingKeyFingerprint,
            contact = ExtensionStore.Contact(
                website = meta.website,
                discord = null,
            ),
            isLegacy = true,
            extensionListUrl = null,
        )
    }
}
