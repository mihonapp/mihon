package tachiyomi.domain.blockrule.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.model.BlockruleUpdate
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class EditBlockrule(
    private val blockruleRepository: BlockruleRepository,
) {

    suspend fun awaitEdit(
        blockruleId: Long,
        name: String? = null,
        type: Blockrule.Type? = null,
        rule: String? = null,
    ) = withNonCancellableContext {
        val update = BlockruleUpdate(
            id = blockruleId,
            name = name,
            type = type?.name,
            rule = rule,
        )

        try {
            blockruleRepository.updatePartial(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun awaitEnable(blockruleId: Long, enable: Boolean) = withNonCancellableContext {
        val e = if (enable) 1L else 0L
        val update = BlockruleUpdate(
            id = blockruleId,
            enable = e,
        )

        try {
            blockruleRepository.updatePartial(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun awaitEdit(
        blockrule: Blockrule,
        name: String? = null,
        type: Blockrule.Type? = null,
        rule: String? = null,
    ) = awaitEdit(
        blockrule.id,
        name,
        type = type,
        rule = rule
    )

    suspend fun awaitEnable(blockrule: Blockrule, enable: Boolean) = awaitEnable(blockrule.id, enable)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
