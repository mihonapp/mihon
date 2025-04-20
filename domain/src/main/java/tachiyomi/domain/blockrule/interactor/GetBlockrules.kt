package tachiyomi.domain.blockrule.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class GetBlockrules(
    private val blockruleRepository: BlockruleRepository,
) {
    fun subscribe(): Flow<List<Blockrule>> {
        return blockruleRepository.getAllAsFlow()
    }

    fun subscribeEnable(enable: Boolean = true): Flow<List<Blockrule>> {
        return blockruleRepository.getEnableAsFlow(enable)
    }

    suspend fun await(): List<Blockrule> {
        return blockruleRepository.getAll()
    }

    suspend fun awaitEnable(enable: Boolean = true): List<Blockrule> {
        return blockruleRepository.getEnable(enable)
    }
}
