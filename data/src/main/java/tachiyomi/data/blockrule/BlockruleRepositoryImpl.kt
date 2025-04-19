package tachiyomi.data.blockrule

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.domain.blockrule.model.BlockruleUpdate
import tachiyomi.domain.blockrule.repository.BlockruleRepository

class BlockruleRepositoryImpl(
    private val handler: DatabaseHandler,
) : BlockruleRepository {

    private fun mapBlockrule(
        id: Long,
        name: String,
        rule: String,
        type: String,
        sort: Long,
        enable: Long,
    ): Blockrule {
        val b = enable != 0L
        val t = Blockrule.Type.valueOf(type)
        return Blockrule(
            id = id,
            name = name,
            sort = sort,
            type = t,
            rule = rule,
            enable = b,
        )
    }

    override suspend fun get(id: Long): Blockrule? {
        return handler.awaitOneOrNull { block_rulesQueries.getBlockRule(id, ::mapBlockrule) }
    }

    override suspend fun getAll(): List<Blockrule> {
        return handler.awaitList { block_rulesQueries.getBlockRules(::mapBlockrule) }
    }

    override fun getAllAsFlow(): Flow<List<Blockrule>> {
        return handler.subscribeToList { block_rulesQueries.getBlockRules(::mapBlockrule) }
    }

    override suspend fun getEnable(enable: Boolean): List<Blockrule> {
        val e = if (enable) 1L else 0L
        return handler.awaitList { block_rulesQueries.getBlockRulesByEnable(e, ::mapBlockrule) }
    }

    override fun getEnableAsFlow(enable: Boolean): Flow<List<Blockrule>> {
        val e = if (enable) 1L else 0L
        return handler.subscribeToList { block_rulesQueries.getBlockRulesByEnable(e, ::mapBlockrule) }
    }

    override suspend fun updatePartial(update: BlockruleUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartial(updates: List<BlockruleUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: BlockruleUpdate) {
        block_rulesQueries.update(
            name = update.name,
            sort = update.sort,
            rule = update.rule,
            type = update.type,
            blockRuleId = update.id,
            enable = update.enable,
        )
    }

    override suspend fun insert(blockrule: Blockrule) {
        handler.await {
            block_rulesQueries.insert(
                name = blockrule.name,
                rule = blockrule.rule,
                type = blockrule.type.name,
                sort = blockrule.sort,
                enable = 1L,
            )
        }
    }

    override suspend fun delete(blockruleId: Long) {
        handler.await {
            block_rulesQueries.delete(
                blockRuleId = blockruleId,
            )
        }
    }
}
