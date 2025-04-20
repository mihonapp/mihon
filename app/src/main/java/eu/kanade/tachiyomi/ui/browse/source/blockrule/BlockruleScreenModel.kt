package eu.kanade.tachiyomi.ui.browse.source.blockrule

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.blockrule.interactor.CreateBlockrule
import tachiyomi.domain.blockrule.interactor.DeleteBlockrule
import tachiyomi.domain.blockrule.interactor.EditBlockrule
import tachiyomi.domain.blockrule.interactor.GetBlockrules
import tachiyomi.domain.blockrule.interactor.ResortBlockrule
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BlockruleScreenModel(
    private val getBlockrules: GetBlockrules = Injekt.get(),
    private val createBlockrule: CreateBlockrule = Injekt.get(),
    private val deleteBlockrule: DeleteBlockrule = Injekt.get(),
    private val resortBlockrule: ResortBlockrule = Injekt.get(),
    private val editBlockrule: EditBlockrule = Injekt.get(),
) : StateScreenModel<BlockruleScreenState>(BlockruleScreenState.Loading) {

    private val _events: Channel<BlockruleEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            getBlockrules.subscribe()
                .collectLatest { blockrules ->
                    mutableState.update {
                        BlockruleScreenState.Success(
                            blockrules = blockrules.toImmutableList(),
                        )
                    }
                }
        }
    }

    fun createBlockrule(
        name: String,
        type: Blockrule.Type,
        rule: String,
    ) {
        screenModelScope.launch {
            when (createBlockrule.await(name, type = type, rule = rule)) {
                is CreateBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                    -> {}
            }
        }
    }

    fun deleteBlockrule(blockruleId: Long) {
        screenModelScope.launch {
            when (deleteBlockrule.await(blockruleId = blockruleId)) {
                is DeleteBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                    -> {}
            }
        }
    }

    fun sortAlphabetically() {
        screenModelScope.launch {
            when (resortBlockrule.sortAlphabetically()) {
                is ResortBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                    -> {}
            }
        }
    }

    fun moveUp(blockrule: Blockrule) {
        screenModelScope.launch {
            when (resortBlockrule.moveUp(blockrule)) {
                is ResortBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                    -> {}
            }
        }
    }

    fun moveDown(blockrule: Blockrule) {
        screenModelScope.launch {
            when (resortBlockrule.moveDown(blockrule)) {
                is ResortBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                    -> {}
            }
        }
    }

    fun editBlockrule(
        blockrule: Blockrule,
        name: String,
        type: Blockrule.Type,
        rule: String,
    ) {
        screenModelScope.launch {
            when (editBlockrule.awaitEdit(blockrule, name = name, type = type, rule = rule)) {
                is EditBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                  -> {}
            }
        }
    }

    fun enableBlockrule(blockrule: Blockrule, enable: Boolean = false) {
        screenModelScope.launch {
            when (editBlockrule.awaitEnable(blockrule, enable)) {
                is EditBlockrule.Result.InternalError -> _events.send(BlockruleEvent.InternalError)
                else                                  -> {}
            }
        }
    }

    fun showDialog(dialog: BlockruleDialog) {
        mutableState.update {
            when (it) {
                BlockruleScreenState.Loading    -> it
                is BlockruleScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                BlockruleScreenState.Loading    -> it
                is BlockruleScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface BlockruleDialog {
    data object Create : BlockruleDialog
    data object SortAlphabetically : BlockruleDialog
    data class Edit(val blockrule: Blockrule) : BlockruleDialog
    data class Delete(val blockrule: Blockrule) : BlockruleDialog
}

sealed interface BlockruleEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : BlockruleEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface BlockruleScreenState {

    @Immutable
    data object Loading : BlockruleScreenState

    @Immutable
    data class Success(
        val blockrules: ImmutableList<Blockrule>,
        val dialog: BlockruleDialog? = null,
    ) : BlockruleScreenState {

        val isEmpty: Boolean
            get() = blockrules.isEmpty()
    }
}
