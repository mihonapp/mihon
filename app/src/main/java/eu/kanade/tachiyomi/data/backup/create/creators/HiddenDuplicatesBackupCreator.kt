package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupHiddenDuplicate
import eu.kanade.tachiyomi.data.backup.models.backupHiddenDuplicateMapper
import tachiyomi.domain.manga.interactor.GetHiddenDuplicates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiddenDuplicatesBackupCreator(
    private val getHiddenDuplicates: GetHiddenDuplicates = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupHiddenDuplicate> {
        return getHiddenDuplicates.await()
            .map(backupHiddenDuplicateMapper)
    }
}
