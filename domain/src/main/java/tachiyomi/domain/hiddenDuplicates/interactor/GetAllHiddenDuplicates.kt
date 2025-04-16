package tachiyomi.domain.hiddenDuplicates.interactor

import tachiyomi.domain.hiddenDuplicates.repository.HiddenDuplicateRepository
import tachiyomi.domain.manga.model.HiddenDuplicate

class GetAllHiddenDuplicates(
    private val hiddenDuplicateRepository: HiddenDuplicateRepository,
) {

    suspend fun await(): List<HiddenDuplicate> {
        return hiddenDuplicateRepository.getAll()
    }
}
