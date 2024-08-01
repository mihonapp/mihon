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
        backupRepo: BackupExtensionRepos,
    ) {
        val dbExtensionRepos = getExtensionRepos.getAll()
        val dbExtensionReposBySHA = dbExtensionRepos.associateBy { it.signingKeyFingerprint }
        val dbExtensionReposByUrl = dbExtensionRepos.associateBy { it.baseUrl }

        val dbExtensionRepoByUrl = dbExtensionReposByUrl[backupRepo.baseUrl]
        val dbExtensionRepoBySHA = dbExtensionReposBySHA[backupRepo.signingKeyFingerprint]

        if (dbExtensionRepoByUrl != null) {
            // URL exists, check fingerprint
            if (dbExtensionRepoByUrl.signingKeyFingerprint != backupRepo.signingKeyFingerprint) {
                error("Already Exists with different signing key fingerprint")
            }
        } else if (dbExtensionRepoBySHA != null) {
            // URL does not exist, check if some other repo has the fingerprint
            error("${dbExtensionRepoBySHA.name} has the same signing key fingerprint")
        } else {
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
    }
}
