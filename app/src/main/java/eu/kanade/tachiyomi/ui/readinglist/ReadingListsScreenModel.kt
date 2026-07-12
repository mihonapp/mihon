package eu.kanade.tachiyomi.ui.readinglist

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.readinglist.cbl.CblParser
import tachiyomi.domain.readinglist.cbl.model.CblParseException
import tachiyomi.domain.readinglist.cbl.model.CblParseFailure
import tachiyomi.domain.readinglist.cbl.model.CblReadingList
import tachiyomi.domain.readinglist.model.ReadingListSummary
import tachiyomi.domain.readinglist.repository.ReadingListRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.Locale

class ReadingListsScreenModel(
    private val application: Application = Injekt.get(),
    private val repository: ReadingListRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<ReadingListsScreenState>(ReadingListsScreenState()) {

    private val parser = CblParser()
    private val documentReader = CblDocumentReader(application.contentResolver)

    private val _events = Channel<ReadingListsEvent>()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            repository.getAllAsFlow().collectLatest { readingLists ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        readingLists = readingLists,
                    )
                }
            }
        }
    }

    fun importDocument(uri: Uri) {
        if (state.value.isImporting) return

        mutableState.update { it.copy(isImporting = true) }
        screenModelScope.launch {
            try {
                val readingList = withIOContext {
                    parser.parse(documentReader.read(uri))
                }
                if (readingList.books.isEmpty()) {
                    _events.send(ReadingListsEvent.ImportFailed(CblImportFailure.EMPTY_READING_LIST))
                    return@launch
                }

                mutableState.update {
                    it.copy(
                        dialog = ReadingListsDialog.SourceSelection(
                            mode = SourceSelectionMode.Import(readingList),
                            listName = readingList.name,
                            entryCount = readingList.books.size,
                            warningCount = readingList.warnings.size,
                            sources = installedSourceOptions(),
                            selectedSourceIds = emptyList(),
                        ),
                    )
                }
            } catch (error: CblDocumentTooLargeException) {
                _events.send(ReadingListsEvent.ImportFailed(CblImportFailure.FILE_TOO_LARGE))
            } catch (error: CblParseException) {
                _events.send(ReadingListsEvent.ImportFailed(error.failure.toImportFailure()))
            } catch (error: SecurityException) {
                _events.send(ReadingListsEvent.ImportFailed(CblImportFailure.CANNOT_OPEN))
            } catch (error: IOException) {
                _events.send(ReadingListsEvent.ImportFailed(CblImportFailure.CANNOT_OPEN))
            } catch (error: Exception) {
                _events.send(ReadingListsEvent.ImportFailed(CblImportFailure.UNKNOWN))
            } finally {
                mutableState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun editSources(readingListId: Long) {
        screenModelScope.launch {
            val readingList = withIOContext { repository.get(readingListId) }
            if (readingList == null) {
                _events.send(ReadingListsEvent.ReadingListMissing)
                return@launch
            }

            val installedSources = installedSourceOptions()
            val installedIds = installedSources.mapTo(mutableSetOf(), ReadingListSourceOption::id)
            val unavailableSources = readingList.selectedSourceIds
                .filterNot(installedIds::contains)
                .map { sourceId ->
                    ReadingListSourceOption(
                        id = sourceId,
                        name = "Unavailable source",
                        language = sourceId.toString(),
                        installed = false,
                    )
                }

            mutableState.update {
                it.copy(
                    dialog = ReadingListsDialog.SourceSelection(
                        mode = SourceSelectionMode.Edit(readingList.id),
                        listName = readingList.name,
                        entryCount = readingList.entries.size,
                        warningCount = readingList.warnings.size,
                        sources = installedSources + unavailableSources,
                        selectedSourceIds = readingList.selectedSourceIds,
                    ),
                )
            }
        }
    }

    fun toggleSource(sourceId: Long) {
        updateSourceDialog { dialog ->
            val selected = if (sourceId in dialog.selectedSourceIds) {
                dialog.selectedSourceIds - sourceId
            } else {
                dialog.selectedSourceIds + sourceId
            }
            dialog.copy(selectedSourceIds = selected)
        }
    }

    fun selectAllInstalledSources() {
        updateSourceDialog { dialog ->
            dialog.copy(
                selectedSourceIds = dialog.sources
                    .filter(ReadingListSourceOption::installed)
                    .map(ReadingListSourceOption::id),
            )
        }
    }

    fun clearSelectedSources() {
        updateSourceDialog { dialog ->
            dialog.copy(selectedSourceIds = emptyList())
        }
    }

    fun moveSelectedSource(sourceId: Long, offset: Int) {
        if (offset == 0) return

        updateSourceDialog { dialog ->
            val currentIndex = dialog.selectedSourceIds.indexOf(sourceId)
            if (currentIndex < 0) return@updateSourceDialog dialog

            val targetIndex = (currentIndex + offset)
                .coerceIn(0, dialog.selectedSourceIds.lastIndex)
            if (currentIndex == targetIndex) return@updateSourceDialog dialog

            val reordered = dialog.selectedSourceIds.toMutableList()
            reordered.add(targetIndex, reordered.removeAt(currentIndex))
            dialog.copy(selectedSourceIds = reordered)
        }
    }

    fun confirmSourceSelection() {
        val dialog = state.value.dialog as? ReadingListsDialog.SourceSelection ?: return
        val selectedInstalledSource = dialog.sources.any { source ->
            source.installed && source.id in dialog.selectedSourceIds
        }
        if (!selectedInstalledSource) {
            screenModelScope.launch {
                _events.send(ReadingListsEvent.SelectInstalledSource)
            }
            return
        }

        mutableState.update { it.copy(isSaving = true) }
        screenModelScope.launch {
            try {
                val saved = withIOContext {
                    when (val mode = dialog.mode) {
                        is SourceSelectionMode.Import -> {
                            repository.insert(mode.readingList, dialog.selectedSourceIds)
                            true
                        }
                        is SourceSelectionMode.Edit -> {
                            repository.updateSources(mode.readingListId, dialog.selectedSourceIds)
                        }
                    }
                }

                if (!saved) {
                    _events.send(ReadingListsEvent.ReadingListMissing)
                    return@launch
                }

                mutableState.update { it.copy(dialog = null) }
                _events.send(
                    when (dialog.mode) {
                        is SourceSelectionMode.Import -> ReadingListsEvent.Imported(dialog.listName)
                        is SourceSelectionMode.Edit -> ReadingListsEvent.SourcesUpdated
                    },
                )
            } catch (error: Exception) {
                _events.send(ReadingListsEvent.SaveFailed)
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun dismissDialog() {
        if (state.value.isSaving) return
        mutableState.update { it.copy(dialog = null) }
    }

    private fun installedSourceOptions(): List<ReadingListSourceOption> {
        return sourceManager.getOnlineSources()
            .distinctBy { source -> source.id }
            .sortedWith(
                compareBy(
                    { source -> source.lang.lowercase(Locale.ROOT) },
                    { source -> source.name.lowercase(Locale.ROOT) },
                    { source -> source.id },
                ),
            )
            .map { source ->
                ReadingListSourceOption(
                    id = source.id,
                    name = source.name,
                    language = source.lang,
                    installed = true,
                )
            }
    }

    private fun updateSourceDialog(
        transform: (ReadingListsDialog.SourceSelection) -> ReadingListsDialog.SourceSelection,
    ) {
        mutableState.update { currentState ->
            val dialog = currentState.dialog as? ReadingListsDialog.SourceSelection
                ?: return@update currentState
            currentState.copy(dialog = transform(dialog))
        }
    }
}

@Immutable
data class ReadingListsScreenState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val isSaving: Boolean = false,
    val readingLists: List<ReadingListSummary> = emptyList(),
    val dialog: ReadingListsDialog? = null,
)

sealed interface ReadingListsDialog {
    data class SourceSelection(
        val mode: SourceSelectionMode,
        val listName: String?,
        val entryCount: Int,
        val warningCount: Int,
        val sources: List<ReadingListSourceOption>,
        val selectedSourceIds: List<Long>,
    ) : ReadingListsDialog {
        val hasInstalledSources: Boolean
            get() = sources.any(ReadingListSourceOption::installed)

        val canConfirm: Boolean
            get() = sources.any { source ->
                source.installed && source.id in selectedSourceIds
            }
    }
}

sealed interface SourceSelectionMode {
    data class Import(val readingList: CblReadingList) : SourceSelectionMode
    data class Edit(val readingListId: Long) : SourceSelectionMode
}

@Immutable
data class ReadingListSourceOption(
    val id: Long,
    val name: String,
    val language: String,
    val installed: Boolean,
)

sealed interface ReadingListsEvent {
    data class Imported(val listName: String?) : ReadingListsEvent
    data class ImportFailed(val failure: CblImportFailure) : ReadingListsEvent
    data object SourcesUpdated : ReadingListsEvent
    data object SelectInstalledSource : ReadingListsEvent
    data object ReadingListMissing : ReadingListsEvent
    data object SaveFailed : ReadingListsEvent
}

enum class CblImportFailure {
    EMPTY_DOCUMENT,
    FILE_TOO_LARGE,
    UNSAFE_XML,
    MALFORMED_XML,
    INVALID_READING_LIST,
    EMPTY_READING_LIST,
    CANNOT_OPEN,
    UNKNOWN,
}

private fun CblParseFailure.toImportFailure(): CblImportFailure {
    return when (this) {
        CblParseFailure.EMPTY_INPUT -> CblImportFailure.EMPTY_DOCUMENT
        CblParseFailure.INPUT_TOO_LARGE,
        CblParseFailure.TOO_MANY_BOOKS,
        -> CblImportFailure.FILE_TOO_LARGE
        CblParseFailure.UNSAFE_XML -> CblImportFailure.UNSAFE_XML
        CblParseFailure.MALFORMED_XML,
        CblParseFailure.MULTIPLE_ROOTS,
        -> CblImportFailure.MALFORMED_XML
        CblParseFailure.INVALID_ROOT,
        CblParseFailure.MISSING_BOOK_ATTRIBUTE,
        CblParseFailure.INVALID_STRUCTURE,
        -> CblImportFailure.INVALID_READING_LIST
    }
}
