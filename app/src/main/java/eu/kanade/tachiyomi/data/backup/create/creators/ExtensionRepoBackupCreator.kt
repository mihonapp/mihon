package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.backupExtensionReposMapper
import mihon.domain.extension.interactor.GetExtensionStores
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionRepoBackupCreator(
    private val getExtensionStores: GetExtensionStores = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getExtensionStores.get()
            .map(backupExtensionReposMapper)
    }
}
