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

    private fun mapHiddenDuplicate(
        id: Long,
        manga1Id: Long,
        manga2Id: Long,
    ): HiddenDuplicate {
        return HiddenDuplicate(
            id = id,
            manga1Id = manga1Id,
            manga2Id = manga2Id,
        )
    }
}
