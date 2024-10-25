package eu.kanade.tachiyomi.ui.libraryUpdateError

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.libraryUpdateError.components.LibraryUpdateErrorUiModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.model.LibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.model.LibraryUpdateErrorMessage
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryUpdateErrorScreenModel(
    private val getLibraryUpdateErrorWithRelations: GetLibraryUpdateErrorWithRelations = Injekt.get(),
    private val getLibraryUpdateErrorMessages: GetLibraryUpdateErrorMessages = Injekt.get(),
) : StateScreenModel<LibraryUpdateErrorScreenState>(LibraryUpdateErrorScreenState()) {

    init {
        screenModelScope.launchIO {
            getLibraryUpdateErrorWithRelations.subscribeAll()
                .collectLatest { errors ->
                    val errorMessages = getLibraryUpdateErrorMessages.await()
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = toLibraryUpdateErrorItems(errors),
                            messages = errorMessages,
                        )
                    }
                }
        }
    }

    private fun toLibraryUpdateErrorItems(errors: List<LibraryUpdateErrorWithRelations>): List<LibraryUpdateErrorItem> {
        return errors.map { error ->
            LibraryUpdateErrorItem(
                error = error,
            )
        }
    }
}

@Immutable
data class LibraryUpdateErrorScreenState(
    val isLoading: Boolean = true,
    val items: List<LibraryUpdateErrorItem> = emptyList(),
    val messages: List<LibraryUpdateErrorMessage> = emptyList(),
) {

    fun getUiModel(): List<LibraryUpdateErrorUiModel> {
        val uiModels = mutableListOf<LibraryUpdateErrorUiModel>()
        val errorMap = items.groupBy { it.error.messageId }
        errorMap.forEach { (messageId, errors) ->
            val message = messages.find { it.id == messageId }
            uiModels.add(LibraryUpdateErrorUiModel.Header(message!!.message))
            uiModels.addAll(errors.map { LibraryUpdateErrorUiModel.Item(it) })
        }
        return uiModels
    }

    fun getHeaderIndexes(): List<Int> = getUiModel()
        .withIndex()
        .filter { it.value is LibraryUpdateErrorUiModel.Header }
        .map { it.index }
}

@Immutable
data class LibraryUpdateErrorItem(
    val error: LibraryUpdateErrorWithRelations,
)
