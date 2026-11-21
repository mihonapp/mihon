package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extension.interactor.GetExtensionStores
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionStoresBackupCreator(
    private val getExtensionStores: GetExtensionStores = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return getExtensionStores.get()
            .map(backupExtensionStoreMapper)
    }
}
