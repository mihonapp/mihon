package tachiyomi.domain.hiddenDuplicates.interactor

import tachiyomi.domain.hiddenDuplicates.repository.HiddenDuplicateRepository

class RemoveHiddenDuplicate(
    private val hiddenDuplicateRepository: HiddenDuplicateRepository,
) {

    suspend operator fun invoke(id1: Long, id2: Long) {
        return hiddenDuplicateRepository.removeHiddenDuplicates(id1, id2)
    }
}
