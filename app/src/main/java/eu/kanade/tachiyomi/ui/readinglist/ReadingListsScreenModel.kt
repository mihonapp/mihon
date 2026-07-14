package eu.kanade.tachiyomi.ui.readinglist

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.Locale

class ReadingListsScreenModel(
    private val application: Application = Injekt.get(),
    private val repository: ReadingListRepository = Injekt.get(),
    private val candidateSearch: ReadingListCandidateSearch = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
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
                            sourceGroups = installedSourceGroups(),
                            selectedSourceIds = emptyList(),
                            preferredLanguage = basePreferences.readingListSourceLanguage.get(),
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

            val installedGroups = installedSourceGroups()
            val installedIds = installedGroups
                .flatMap(ReadingListSourceGroup::sources)
                .mapTo(mutableSetOf(), ReadingListSourceOption::id)
            val unavailableSources = readingList.selectedSourceIds
                .filterNot(installedIds::contains)
                .map { sourceId ->
                    ReadingListSourceOption(
                        id = sourceId,
                        name = sourceId.toString(),
                        language = "",
                        installed = false,
                    )
                }
            val unavailableGroup = unavailableSources
                .takeIf(List<ReadingListSourceOption>::isNotEmpty)
                ?.let { sources ->
                    ReadingListSourceGroup(
                        key = UNAVAILABLE_SOURCE_GROUP_KEY,
                        extensionName = "",
                        packageName = null,
                        installed = false,
                        sources = sources,
                    )
                }

            mutableState.update {
                it.copy(
                    dialog = ReadingListsDialog.SourceSelection(
                        mode = SourceSelectionMode.Edit(readingList.id),
                        listName = readingList.name,
                        entryCount = readingList.entries.size,
                        warningCount = readingList.warnings.size,
                        sourceGroups = installedGroups + listOfNotNull(unavailableGroup),
                        selectedSourceIds = readingList.selectedSourceIds,
                        preferredLanguage = basePreferences.readingListSourceLanguage.get(),
                    ),
                )
            }
        }
    }

    fun searchCandidates(readingListId: Long) {
        if (readingListId in state.value.searchingReadingListIds) return

        mutableState.update { currentState ->
            currentState.copy(
                searchingReadingListIds = currentState.searchingReadingListIds + readingListId,
            )
        }
        screenModelScope.launch {
            try {
                when (val result = withIOContext { candidateSearch.search(readingListId) }) {
                    ReadingListCandidateSearchResult.ReadingListNotFound -> {
                        _events.send(ReadingListsEvent.ReadingListMissing)
                    }
                    is ReadingListCandidateSearchResult.Completed -> {
                        val summary = result.summary
                        val missingReadingList =
                            summary.searchedEntries == 0 && summary.missingEntries > 0
                        val noInstalledSources =
                            summary.allSelectedSourcesUnavailable &&
                                summary.sourceUnavailableEntries > 0 &&
                                summary.autoMatchedEntries == 0 &&
                                summary.reviewEntries == 0 &&
                                summary.unresolvedEntries == 0
                        _events.send(
                            when {
                                missingReadingList -> ReadingListsEvent.ReadingListMissing
                                summary.searchedEntries == 0 -> ReadingListsEvent.CandidateSearchNothingToDo
                                noInstalledSources -> ReadingListsEvent.CandidateSearchNoInstalledSources
                                else -> ReadingListsEvent.CandidateSearchCompleted(summary)
                            },
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(ReadingListsEvent.CandidateSearchFailed)
            } finally {
                mutableState.update { currentState ->
                    currentState.copy(
                        searchingReadingListIds =
                        currentState.searchingReadingListIds - readingListId,
                    )
                }
            }
        }
    }

    fun requestDelete(readingList: ReadingListSummary) {
        mutableState.update {
            it.copy(dialog = ReadingListsDialog.DeleteConfirmation(readingList))
        }
    }

    fun confirmDelete() {
        val dialog = state.value.dialog as? ReadingListsDialog.DeleteConfirmation ?: return
        if (state.value.isDeleting) return

        mutableState.update { it.copy(isDeleting = true) }
        screenModelScope.launch {
            try {
                withIOContext {
                    repository.delete(dialog.readingList.id)
                }
                mutableState.update { it.copy(dialog = null) }
                _events.send(ReadingListsEvent.Deleted(dialog.readingList.name))
            } catch (error: Exception) {
                _events.send(ReadingListsEvent.DeleteFailed)
            } finally {
                mutableState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun setPreferredLanguage(language: String) {
        val normalizedLanguage = language.trim().lowercase(Locale.ROOT)
        basePreferences.readingListSourceLanguage.set(normalizedLanguage)
        updateSourceDialog { dialog ->
            dialog.copy(preferredLanguage = normalizedLanguage)
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

    fun toggleSources(sourceIds: List<Long>) {
        if (sourceIds.isEmpty()) return

        updateSourceDialog { dialog ->
            val uniqueSourceIds = sourceIds.distinct()
            val allSelected = uniqueSourceIds.all(dialog.selectedSourceIds::contains)
            val selected = if (allSelected) {
                val sourceIdSet = uniqueSourceIds.toSet()
                dialog.selectedSourceIds.filterNot(sourceIdSet::contains)
            } else {
                dialog.selectedSourceIds + uniqueSourceIds.filterNot(dialog.selectedSourceIds::contains)
            }
            dialog.copy(selectedSourceIds = selected)
        }
    }

    fun selectSources(sourceIds: List<Long>) {
        if (sourceIds.isEmpty()) return

        updateSourceDialog { dialog ->
            dialog.copy(
                selectedSourceIds = dialog.selectedSourceIds +
                    sourceIds.distinct().filterNot(dialog.selectedSourceIds::contains),
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
        val selectedInstalledSource = dialog.sourceGroups
            .flatMap(ReadingListSourceGroup::sources)
            .any { source ->
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
        if (state.value.isSaving || state.value.isDeleting) return
        mutableState.update { it.copy(dialog = null) }
    }

    private suspend fun installedSourceGroups(): List<ReadingListSourceGroup> {
        extensionManager.isInitialized.first { initialized -> initialized }

        val extensions = extensionManager.installedExtensionsFlow.value.map { extension ->
            InstalledReadingListExtension(
                extensionName = extension.name,
                packageName = extension.pkgName,
                sources = extension.sources
                    .filterIsInstance<HttpSource>()
                    .map { source ->
                        ReadingListSourceOption(
                            id = source.id,
                            name = source.name,
                            language = source.lang,
                            installed = true,
                        )
                    },
            )
        }
        return buildReadingListSourceGroups(extensions)
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
    val isDeleting: Boolean = false,
    val searchingReadingListIds: Set<Long> = emptySet(),
    val readingLists: List<ReadingListSummary> = emptyList(),
    val dialog: ReadingListsDialog? = null,
)

sealed interface ReadingListsDialog {
    data class SourceSelection(
        val mode: SourceSelectionMode,
        val listName: String?,
        val entryCount: Int,
        val warningCount: Int,
        val sourceGroups: List<ReadingListSourceGroup>,
        val selectedSourceIds: List<Long>,
        val preferredLanguage: String,
    ) : ReadingListsDialog {
        val hasInstalledSources: Boolean
            get() = sourceGroups
                .flatMap(ReadingListSourceGroup::sources)
                .any(ReadingListSourceOption::installed)

        val canConfirm: Boolean
            get() = sourceGroups
                .flatMap(ReadingListSourceGroup::sources)
                .any { source ->
                    source.installed && source.id in selectedSourceIds
                }
    }

    data class DeleteConfirmation(
        val readingList: ReadingListSummary,
    ) : ReadingListsDialog
}

sealed interface SourceSelectionMode {
    data class Import(val readingList: CblReadingList) : SourceSelectionMode
    data class Edit(val readingListId: Long) : SourceSelectionMode
}

@Immutable
data class ReadingListSourceGroup(
    val key: String,
    val extensionName: String,
    val packageName: String?,
    val installed: Boolean,
    val sources: List<ReadingListSourceOption>,
)

@Immutable
data class ReadingListSourceOption(
    val id: Long,
    val name: String,
    val language: String,
    val installed: Boolean,
)

internal data class InstalledReadingListExtension(
    val extensionName: String,
    val packageName: String,
    val sources: List<ReadingListSourceOption>,
)

internal fun buildReadingListSourceGroups(
    extensions: List<InstalledReadingListExtension>,
): List<ReadingListSourceGroup> {
    val seenSourceIds = mutableSetOf<Long>()

    return extensions
        .sortedWith(
            compareBy(
                { extension -> extension.extensionName.lowercase(Locale.ROOT) },
                InstalledReadingListExtension::packageName,
            ),
        )
        .mapNotNull { extension ->
            val sources = extension.sources
                .sortedWith(
                    compareBy(
                        { source -> source.language.lowercase(Locale.ROOT) },
                        { source -> source.name.lowercase(Locale.ROOT) },
                        ReadingListSourceOption::id,
                    ),
                )
                .filter { source -> seenSourceIds.add(source.id) }
            if (sources.isEmpty()) return@mapNotNull null

            ReadingListSourceGroup(
                key = extension.packageName,
                extensionName = extension.extensionName,
                packageName = extension.packageName,
                installed = true,
                sources = sources,
            )
        }
}

sealed interface ReadingListsEvent {
    data class Imported(val listName: String?) : ReadingListsEvent
    data class ImportFailed(val failure: CblImportFailure) : ReadingListsEvent
    data class Deleted(val listName: String?) : ReadingListsEvent
    data object SourcesUpdated : ReadingListsEvent
    data class CandidateSearchCompleted(
        val summary: ReadingListCandidateSearchSummary,
    ) : ReadingListsEvent
    data object CandidateSearchNoInstalledSources : ReadingListsEvent
    data object CandidateSearchNothingToDo : ReadingListsEvent
    data object CandidateSearchFailed : ReadingListsEvent
    data object SelectInstalledSource : ReadingListsEvent
    data object ReadingListMissing : ReadingListsEvent
    data object SaveFailed : ReadingListsEvent
    data object DeleteFailed : ReadingListsEvent
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

private const val UNAVAILABLE_SOURCE_GROUP_KEY = "reading-list-unavailable-sources"
