package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.domain.extension.model.ExtensionStore

@Serializable
class BackupExtensionRepos(
    @ProtoNumber(1) var indexUrl: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var badgeLabel: String?,
    @ProtoNumber(5) var signingKey: String,
    @ProtoNumber(4) var contactWebsite: String?,
    @ProtoNumber(6) var contactDiscord: String?,
    @ProtoNumber(7) var isLegacy: Boolean,
)

val backupExtensionReposMapper = { repo: ExtensionStore ->
    BackupExtensionRepos(
        indexUrl = repo.indexUrl,
        name = repo.name,
        badgeLabel = repo.badgeLabel,
        signingKey = repo.signingKey,
        contactWebsite = repo.contact.website,
        contactDiscord = repo.contact.discord,
        isLegacy = repo.isLegacy,
    )
}
