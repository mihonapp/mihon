package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupHiddenDuplicate
import eu.kanade.tachiyomi.data.backup.models.backupHiddenDuplicateMapper
import tachiyomi.domain.hiddenDuplicates.interactor.GetAllHiddenDuplicates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiddenDuplicatesBackupCreator(
    private val getAllHiddenDuplicates: GetAllHiddenDuplicates = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupHiddenDuplicate> {
        return getAllHiddenDuplicates.await()
            .map(backupHiddenDuplicateMapper)
    }
}
