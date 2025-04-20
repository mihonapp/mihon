package tachiyomi.domain.blockrule.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.model.BlockruleUpdate

interface BlockruleRepository {

    suspend fun get(id: Long): Blockrule?

    suspend fun getAll(): List<Blockrule>

    fun getAllAsFlow(): Flow<List<Blockrule>>

    suspend fun getEnable(enable: Boolean = true): List<Blockrule>

    fun getEnableAsFlow(enable: Boolean = true): Flow<List<Blockrule>>

    suspend fun insert(blockrule: Blockrule)

    suspend fun delete(blockruleId: Long)

    suspend fun updatePartial(update: BlockruleUpdate)

    suspend fun updatePartial(updates: List<BlockruleUpdate>)
}
