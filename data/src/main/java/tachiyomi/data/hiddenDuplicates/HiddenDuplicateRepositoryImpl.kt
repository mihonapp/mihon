package tachiyomi.data.hiddenDuplicates

import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.hiddenDuplicates.repository.HiddenDuplicateRepository
import tachiyomi.domain.manga.model.HiddenDuplicate

class HiddenDuplicateRepositoryImpl(
    private val handler: DatabaseHandler,
) : HiddenDuplicateRepository {

    override suspend fun getAll(): List<HiddenDuplicate> {
        return handler.awaitList { hidden_duplicatesQueries.getAll(::mapHiddenDuplicate) }
    }

    override suspend fun addHiddenDuplicate(id1: Long, id2: Long) {
        handler.await(inTransaction = true) { hidden_duplicatesQueries.insert(id1, id2) }
    }

    override suspend fun removeHiddenDuplicates(id1: Long, id2: Long) {
        handler.await(inTransaction = true) { hidden_duplicatesQueries.remove(id1, id2) }
    }

    private fun mapHiddenDuplicate(
        manga1Id: Long,
        manga2Id: Long,
    ): HiddenDuplicate {
        return HiddenDuplicate(
            manga1Id = manga1Id,
            manga2Id = manga2Id,
        )
    }
}
