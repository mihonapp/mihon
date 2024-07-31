package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.interactor.GetExtensionRepo
import tachiyomi.data.DatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionRepoRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetExtensionRepo = Injekt.get()
) {

    suspend operator fun invoke(backupExtensionRepos: List<BackupExtensionRepos>) {
        if (backupExtensionRepos.isEmpty()) return

        val dbExtensionRepos = getExtensionRepos.getAll()
        val dbExtensionReposBySHA = dbExtensionRepos.associateBy { it.signingKeyFingerprint }

        backupExtensionRepos
            .sortedBy { it.baseUrl }
            .forEach { backupRepo ->
                val dbExtensionRepo = dbExtensionReposBySHA[backupRepo.signingKeyFingerprint]
                if (dbExtensionRepo == null) {
                    handler.await {
                        extension_reposQueries.insert(
                            backupRepo.baseUrl,
                            backupRepo.name,
                            backupRepo.shortName,
                            backupRepo.website,
                            backupRepo.signingKeyFingerprint
                        )
                    }
                } else {
                    error("Extension Repo not added: ${backupRepo.name} has the same signature as an existing repo")
                }
            }
    }
}
