package tachiyomi.domain.blockrule.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.blockrule.model.BlockruleUpdate
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class UpdateBlockrule(
    private val blockruleRepository: BlockruleRepository,
) {

    suspend fun await(payload: BlockruleUpdate): Result = withNonCancellableContext {
        try {
            blockruleRepository.updatePartial(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
