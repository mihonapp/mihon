package tachiyomi.domain.blockrule.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.model.BlockruleUpdate
import tachiyomi.domain.blockrule.repository.BlockruleRepository
import java.util.*

class ResortBlockrule(
    private val blockruleRepository: BlockruleRepository,
) {

    private val mutex = Mutex()

    suspend fun moveUp(blockrule: Blockrule): Result = await(blockrule, MoveTo.UP)

    suspend fun moveDown(blockrule: Blockrule): Result = await(blockrule, MoveTo.DOWN)

    private suspend fun await(blockrule: Blockrule, moveTo: MoveTo) = withNonCancellableContext {
        mutex.withLock {
            val blockrules = blockruleRepository.getAll().toMutableList()

            val currentIndex = blockrules.indexOfFirst { it.id == blockrule.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            val newPosition = when (moveTo) {
                MoveTo.UP   -> currentIndex - 1
                MoveTo.DOWN -> currentIndex + 1
            }.toInt()

            try {
                Collections.swap(blockrules, currentIndex, newPosition)

                val updates = blockrules.mapIndexed { index, blockrule ->
                    BlockruleUpdate(
                        id = blockrule.id,
                        sort = index.toLong(),
                    )
                }

                blockruleRepository.updatePartial(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    suspend fun sortAlphabetically() = withNonCancellableContext {
        mutex.withLock {
            val updates = blockruleRepository.getAll()
                .sortedBy { blockrule -> blockrule.name }
                .mapIndexed { index, blockrule ->
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
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }

    private enum class MoveTo {
        UP,
        DOWN,
    }
}
