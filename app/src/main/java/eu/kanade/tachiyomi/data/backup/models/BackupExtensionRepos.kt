package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extensionrepo.model.ExtensionRepo

@Serializable
class BackupExtensionRepos(
    @ProtoNumber(1) var baseUrl: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var shortName: String?,
    @ProtoNumber(4) var website: String,
    @ProtoNumber(5) var signingKeyFingerprint: String,
)

val backupExtensionReposMapper = { repo: ExtensionRepo ->
    BackupExtensionRepos(
        baseUrl = repo.baseUrl,
        name = repo.name,
        shortName = repo.shortName,
        website = repo.website,
        signingKeyFingerprint = repo.signingKeyFingerprint,
    )
}
