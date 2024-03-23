package mihon.domain.extensionrepo.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mihon.domain.extensionrepo.model.ExtensionRepo

@Serializable
data class ExtensionRepoMetaDto(
    @SerialName("meta")
    val repo: ExtensionRepoDto,
)

@Serializable
data class ExtensionRepoDto(
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
)

fun ExtensionRepoMetaDto.toExtensionRepo(baseUrl: String): ExtensionRepo {
    return ExtensionRepo(
        baseUrl = baseUrl,
        name = repo.name,
        shortName = repo.shortName,
        website = repo.website,
        signingKeyFingerprint = repo.signingKeyFingerprint,
    )
}
