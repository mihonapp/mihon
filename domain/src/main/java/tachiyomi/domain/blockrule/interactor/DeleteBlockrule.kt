package tachiyomi.domain.blockrule.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.blockrule.model.BlockruleUpdate
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class DeleteBlockrule(
    private val blockruleRepository: BlockruleRepository,
) {

    suspend fun await(blockruleId: Long) = withNonCancellableContext {
        try {
            blockruleRepository.delete(blockruleId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val blockrules = blockruleRepository.getAll()
        val updates = blockrules.mapIndexed { index, blockrule ->
            BlockruleUpdate(
                id = blockrule.id,
                sort = index.toLong(),
            )
        }

        try {
            blockruleRepository.updatePartial(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
