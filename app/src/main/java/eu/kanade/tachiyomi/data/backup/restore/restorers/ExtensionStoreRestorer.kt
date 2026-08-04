package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import tachiyomi.data.Database
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoreRestorer(
    private val database: Database = Injekt.get(),
) {

    suspend operator fun invoke(
        backupStore: BackupExtensionStore,
    ) {
        database.extension_storeQueries.upsert(
            indexUrl = backupStore.indexUrl,
            name = backupStore.name,
            badgeLabel = backupStore.badgeLabel ?: backupStore.name,
            signingKey = backupStore.signingKey,
            contactWebsite = backupStore.contactWebsite,
            contactDiscord = backupStore.contactDiscord,
            isLegacy = backupStore.isLegacy ?: true,
            extensionListUrl = backupStore.extensionListUrl,
        )
    }
}
