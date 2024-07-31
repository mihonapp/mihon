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

    suspend operator fun invoke(
        backupExtensionRepos: List<BackupExtensionRepos>,
        onError: (String) -> Unit
    ) {
        if (backupExtensionRepos.isEmpty()) return

        val dbExtensionRepos = getExtensionRepos.getAll()
        val dbExtensionReposBySHA = dbExtensionRepos.associateBy { it.signingKeyFingerprint }
        val dbExtensionReposByUrl = dbExtensionRepos.associateBy { it.baseUrl }

        backupExtensionRepos.forEach { backupRepo ->
            try {
                val dbExtensionRepoByUrl = dbExtensionReposByUrl[backupRepo.baseUrl]
                val dbExtensionRepoBySHA = dbExtensionReposBySHA[backupRepo.signingKeyFingerprint]

                if (dbExtensionRepoByUrl != null) {
                    // URL exists, check fingerprint
                    if (dbExtensionRepoByUrl.signingKeyFingerprint != backupRepo.signingKeyFingerprint) {
                        onError("Fingerprint mismatch for ${backupRepo.baseUrl}")
                    }
                } else if (dbExtensionRepoBySHA != null) {
                    // URL does not exist, check if some other repo has the fingerprint
                    onError("Fingerprint already exists for another repo: ${dbExtensionRepoBySHA.baseUrl}")
                }
                else {
                    // Restore backup
                    handler.await {
                        extension_reposQueries.insert(
                            backupRepo.baseUrl,
                            backupRepo.name,
                            backupRepo.shortName,
                            backupRepo.website,
                            backupRepo.signingKeyFingerprint
                        )
                    }
                }
            } catch (e: Exception) {
                onError("Error restoring ${backupRepo.baseUrl}: ${e.message}")
            }
        }
    }
}
