package tachiyomi.domain.blockrule.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class CreateBlockrule(
    private val blockruleRepository: BlockruleRepository,
) {
    suspend fun await(name: String, type : Blockrule.Type, rule: String): Result = withNonCancellableContext {
        val blockrules = blockruleRepository.getAll()
        val nextOrder = blockrules.maxOfOrNull { it.sort }?.plus(1) ?: 0

        val newBlockrule = Blockrule(
            id = 0,
            name = name,
            sort = nextOrder,
            type = type,
            rule = rule
        )

        try {
            blockruleRepository.insert(newBlockrule)
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
