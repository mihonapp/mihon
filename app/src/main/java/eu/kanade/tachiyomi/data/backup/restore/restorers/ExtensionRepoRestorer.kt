package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import tachiyomi.data.Database
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionRepoRestorer(
    private val database: Database = Injekt.get(),
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        database.extension_storeQueries.upsert(
            indexUrl = backupRepo.indexUrl,
            name = backupRepo.name,
            badgeLabel = backupRepo.badgeLabel ?: backupRepo.name,
            signingKey = backupRepo.signingKey,
            contactWebsite = backupRepo.contactWebsite ?: backupRepo.indexUrl,
            contactDiscord = backupRepo.contactDiscord,
            isLegacy = backupRepo.isLegacy,
        )
    }
}
