package tachiyomi.domain.hiddenDuplicates.interactor

import tachiyomi.domain.hiddenDuplicates.repository.HiddenDuplicateRepository

class AddHiddenDuplicate(
    private val hiddenDuplicateRepository: HiddenDuplicateRepository,
) {
    suspend operator fun invoke(id1: Long, id2: Long) {
        hiddenDuplicateRepository.addHiddenDuplicate(id1, id2)
    }
}
