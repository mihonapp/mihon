package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupHiddenDuplicate
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.hiddenDuplicates.interactor.GetAllHiddenDuplicates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiddenDuplicatesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getAllHiddenDuplicates: GetAllHiddenDuplicates = Injekt.get(),
) {

    suspend operator fun invoke(backupHiddenDuplicates: List<BackupHiddenDuplicate>) {
        if (backupHiddenDuplicates.isNotEmpty()) {
            val dbHiddenDuplicates = getAllHiddenDuplicates.await()
                .map { BackupHiddenDuplicate(it.manga1Id, it.manga2Id) }
                .toMutableList()

            backupHiddenDuplicates.map {
                if (isHiddenDupeInDB(it, dbHiddenDuplicates)) return@map
                handler.await { hidden_duplicatesQueries.insert(it.manga1Id, it.manga2Id) }
                dbHiddenDuplicates.add(BackupHiddenDuplicate(it.manga1Id, it.manga2Id))
            }
        }
    }
}

private fun isHiddenDupeInDB(
    backupHiddenDupe: BackupHiddenDuplicate,
    databaseList: List<BackupHiddenDuplicate>,
): Boolean {
    databaseList.forEach {
        when (Pair(it.manga1Id, it.manga2Id)) {
            Pair(backupHiddenDupe.manga1Id, backupHiddenDupe.manga2Id) -> return true
            Pair(backupHiddenDupe.manga2Id, backupHiddenDupe.manga1Id) -> return true
        }
    }
    return false
}
