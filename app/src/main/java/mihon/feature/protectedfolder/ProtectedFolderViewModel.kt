package mihon.feature.protectedfolder

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.core.preference.asState
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.protectedfolder.interactor.GetProtectedLibraryManga
import mihon.domain.protectedfolder.interactor.SetMangaProtected
import mihon.domain.protectedfolder.repository.ProtectedMangaRepository
import mihon.domain.protectedfolder.service.PinHasher
import mihon.domain.protectedfolder.service.ProtectedFolderPin
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.isLocal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class ProtectedFolderViewModel(
    @Assisted private val purpose: Purpose,
    private val pin: ProtectedFolderPin,
    private val session: ProtectedFolderSession,
    private val wipeProtectedFolder: WipeProtectedFolder,
    private val getProtectedLibraryManga: GetProtectedLibraryManga,
    private val setMangaProtected: SetMangaProtected,
    private val protectedMangaRepository: ProtectedMangaRepository,
    private val libraryPreferences: LibraryPreferences,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
) : ViewModel() {

    private val _state = MutableStateFlow(
        State(stage = if (pin.isSet) Stage.EnterPin else Stage.CreatePin(firstEntry = null)),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    private var lockoutTicker: Job? = null

    init {
        tickLockout()
        viewModelScope.launch {
            combine(
                getProtectedLibraryManga.subscribe(),
                downloadCache.changes,
            ) { manga, _ -> manga.toItems() }
                .flowOn(Dispatchers.Default)
                .collectLatest { items ->
                    _state.update { state ->
                        val ids = items.mapTo(mutableSetOf()) { it.id }
                        state.copy(items = items, selection = state.selection.intersect(ids))
                    }
                }
        }
    }

    private suspend fun List<LibraryManga>.toItems(): List<LibraryItem> {
        val sources = mapTo(mutableSetOf()) { it.manga.source }.associateWith { sourceManager.getOrStub(it) }
        val downloadCounts = downloadManager.getDownloadCounts(map { it.manga })
        return sortedWith { a, b -> a.manga.title.compareToWithCollator(b.manga.title) }
            .map { manga ->
                val source = sources.getValue(manga.manga.source)
                val downloadCount = downloadCounts.getValue(manga.id)
                val isLocal = manga.manga.isLocal()
                LibraryItem(
                    libraryManga = manga,
                    downloadCount = downloadCount,
                    unreadCount = manga.unreadCount,
                    isLocal = isLocal,
                    sourceName = source.name.lowercase(),
                    sourceLanguage = source.lang,
                    badges = LibraryItem.Badges(
                        downloadCount = if (libraryPreferences.downloadBadge.get()) downloadCount else 0,
                        unreadCount = if (libraryPreferences.unreadBadge.get()) manga.unreadCount else 0,
                        isLocal = libraryPreferences.localBadge.get() && isLocal,
                        sourceLanguage = if (libraryPreferences.languageBadge.get()) source.lang else "",
                    ),
                )
            }
    }

    fun onDigit(digit: Int) {
        val state = _state.value
        if (state.stage == Stage.Unlocked || state.busy || state.lockoutRemaining > Duration.ZERO) return
        if (digit !in 0..9 || state.input.length >= PinHasher.PIN_LENGTH) return

        val input = state.input + digit
        _state.update { it.copy(input = input, message = null) }
        if (input.length == PinHasher.PIN_LENGTH) submit(input)
    }

    fun onBackspace() {
        _state.update { if (it.busy) it else it.copy(input = it.input.dropLast(1)) }
    }

    private fun submit(input: String) {
        when (val stage = _state.value.stage) {
            Stage.EnterPin -> verify(input)
            is Stage.CreatePin -> when (stage.firstEntry) {
                null -> _state.update { it.copy(stage = Stage.CreatePin(firstEntry = input), input = "") }
                input -> create(input)
                else -> _state.update {
                    it.copy(
                        stage = Stage.CreatePin(firstEntry = null),
                        input = "",
                        message = MR.strings.protected_folder_pin_mismatch,
                    )
                }
            }
            Stage.Unlocked -> {}
        }
    }

    private fun verify(input: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            // Hashing takes long enough to drop frames on the main thread
            val result = withContext(Dispatchers.Default) { pin.verify(input) }
            _state.update { it.copy(busy = false, input = "") }
            when (result) {
                ProtectedFolderPin.Result.Success -> onVerified()
                is ProtectedFolderPin.Result.Wrong, is ProtectedFolderPin.Result.LockedOut -> {
                    _state.update { it.copy(message = MR.strings.protected_folder_pin_wrong) }
                    tickLockout()
                }
            }
        }
    }

    private fun onVerified() {
        when (purpose) {
            Purpose.Open -> open()
            Purpose.ChangePin -> _state.update { it.copy(stage = Stage.CreatePin(firstEntry = null)) }
            Purpose.RemovePin -> _state.update { it.copy(dialog = Dialog.RemovePin) }
        }
    }

    private fun create(input: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.Default) { pin.set(input) }
            _state.update { it.copy(busy = false, input = "") }
            when (purpose) {
                Purpose.Open -> open()
                Purpose.ChangePin, Purpose.RemovePin -> {
                    _events.send(Event.Finished(MR.strings.protected_folder_pin_changed))
                }
            }
        }
    }

    private fun open() {
        session.unlock()
        _state.update { it.copy(stage = Stage.Unlocked, message = null) }
    }

    /**
     * The device has already confirmed who is holding it.
     */
    fun onBiometricSuccess() {
        if (purpose == Purpose.Open && _state.value.stage == Stage.EnterPin) open()
    }

    private fun tickLockout() {
        lockoutTicker?.cancel()
        lockoutTicker = viewModelScope.launch {
            while (true) {
                val remaining = pin.lockoutRemaining()
                _state.update { it.copy(lockoutRemaining = remaining) }
                if (remaining <= Duration.ZERO) break
                delay(1.seconds)
            }
        }
    }

    fun showForgotPinDialog() {
        _state.update { it.copy(dialog = Dialog.ForgotPin) }
    }

    fun wipe() {
        _state.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            val result = wipeProtectedFolder.await()
            _state.update { it.copy(busy = false, input = "") }
            when (result) {
                WipeProtectedFolder.Result.Failed -> {
                    _events.send(Event.Message(MR.strings.protected_folder_wipe_failed))
                }
                WipeProtectedFolder.Result.Success, is WipeProtectedFolder.Result.FilesLeft -> {
                    if (result is WipeProtectedFolder.Result.FilesLeft) {
                        _events.send(Event.Message(MR.strings.protected_folder_wipe_files_left))
                    }
                    lockoutTicker?.cancel()
                    _state.update {
                        it.copy(
                            stage = Stage.CreatePin(firstEntry = null),
                            message = MR.strings.protected_folder_wiped,
                            lockoutRemaining = Duration.ZERO,
                        )
                    }
                }
            }
        }
    }

    fun removePin() {
        _state.update { it.copy(dialog = null, busy = true) }
        viewModelScope.launch {
            protectedMangaRepository.unprotectAll()
            pin.clear()
            session.lock()
            _events.send(Event.Finished(MR.strings.protected_folder_pin_removed))
        }
    }

    fun toggleSelection(manga: LibraryManga) {
        _state.update {
            it.copy(selection = if (manga.id in it.selection) it.selection - manga.id else it.selection + manga.id)
        }
    }

    fun selectAll() {
        _state.update { state -> state.copy(selection = state.items.mapTo(mutableSetOf()) { it.id }) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun showRemoveFromFolderDialog() {
        _state.update { it.copy(dialog = Dialog.RemoveFromFolder(it.selection.toList())) }
    }

    fun removeFromFolder(mangaIds: List<Long>) {
        _state.update { it.copy(dialog = null, selection = emptySet()) }
        viewModelScope.launch { setMangaProtected.await(mangaIds, protected = false) }
    }

    fun showOptionsDialog() {
        _state.update { it.copy(dialog = Dialog.Options) }
    }

    fun dismissDialog() {
        val wasRemovingPin = _state.value.dialog == Dialog.RemovePin
        _state.update { it.copy(dialog = null) }
        // The PIN was only entered to remove it, so backing out of that leaves nothing to do here
        if (wasRemovingPin) _events.trySend(Event.Finished(message = null))
    }

    fun getDisplayMode(): PreferenceMutableState<LibraryDisplayMode> {
        return libraryPreferences.displayMode.asState(viewModelScope)
    }

    fun getColumnsForOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns else libraryPreferences.portraitColumns)
            .asState(viewModelScope)
    }

    enum class Purpose {
        Open,
        ChangePin,
        RemovePin,
    }

    sealed interface Stage {
        data object EnterPin : Stage

        /**
         * @param firstEntry the new PIN as first typed, once it is waiting to be typed again.
         */
        data class CreatePin(val firstEntry: String?) : Stage

        data object Unlocked : Stage
    }

    sealed interface Dialog {
        data object ForgotPin : Dialog
        data object RemovePin : Dialog
        data object Options : Dialog
        data class RemoveFromFolder(val mangaIds: List<Long>) : Dialog
    }

    sealed interface Event {
        data class Message(val message: StringResource) : Event
        data class Finished(val message: StringResource?) : Event
    }

    @Immutable
    data class State(
        val stage: Stage,
        val input: String = "",
        val message: StringResource? = null,
        val lockoutRemaining: Duration = Duration.ZERO,
        val busy: Boolean = false,
        val items: List<LibraryItem> = emptyList(),
        val selection: Set<Long> = emptySet(),
        val dialog: Dialog? = null,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(purpose: Purpose): ProtectedFolderViewModel
    }
}
