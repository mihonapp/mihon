package mihon.feature.extension.missing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

/**
 * Backs both the missing-extensions list and the dialog a stranded entry puts up, since they do the
 * same two things: work out what is missing, and install one of them on request.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MissingExtensionsViewModel(
    private val finder: MissingExtensionFinder,
    private val extensionManager: ExtensionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var batchJob: Job? = null

    /**
     * Everything this device is missing.
     */
    fun refresh(force: Boolean = false) {
        _state.update { State(loading = true) }

        viewModelScope.launchIO {
            try {
                val items = finder.findAll(refresh = force)
                _state.update { it.copy(loading = false, items = items) }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not list the missing extensions" }
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /**
     * Just the one extension that would make [sourceId] readable, for the dialog.
     */
    fun loadForSource(sourceId: Long) {
        // A fresh start, installs included: this screen may share its view model with the list,
        // and a leftover "installed" would make the dialog congratulate itself and close at once.
        _state.update { State(loading = true) }

        viewModelScope.launchIO {
            try {
                val extension = finder.forSource(sourceId)
                val items = extension
                    ?.let { listOf(MissingExtension(it, entryCount = 1, sourceNames = emptyList())) }
                    .orEmpty()
                _state.update { it.copy(loading = false, items = items) }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not look up the extension for source $sourceId" }
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun install(missing: MissingExtension) {
        if (_state.value.isInstalling) return

        viewModelScope.launchIO { awaitInstall(missing) }
    }

    /**
     * Installs everything that is missing, one at a time.
     *
     * Strictly one at a time, because an install usually ends in a system dialog the user has to
     * confirm: firing several would stack those dialogs, and Android drops the ones it cannot show.
     * Mihon's own install service processes a single entry at a time anyway, so nothing is gained
     * by queueing more.
     */
    fun installAll() {
        if (batchJob?.isActive == true) return

        val targets = _state.value.items
        if (targets.isEmpty()) return

        _state.update { it.copy(batch = Batch(total = targets.size, done = 0)) }

        batchJob = viewModelScope.launchIO {
            var current: MissingExtension? = null
            try {
                targets.forEachIndexed { index, missing ->
                    current = missing
                    awaitInstall(missing)
                    _state.update { it.copy(batch = it.batch?.copy(done = index + 1)) }
                }
            } finally {
                if (!isActive) current?.let { extensionManager.cancelInstallUpdateExtension(it.extension) }
                _state.update { state ->
                    // Keep the failures on screen; drop the steps left mid-flight by a cancellation.
                    state.copy(batch = null, installs = state.installs.filterValues { it is InstallStep.Error })
                }
            }
        }
    }

    fun cancelAll() {
        batchJob?.cancel()
    }

    private suspend fun awaitInstall(missing: MissingExtension) {
        val pkgName = missing.extension.pkgName
        setStep(pkgName, InstallStep.Pending)

        try {
            val finished = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
                extensionManager.installExtension(missing.extension)
                    .onEach { step -> setStep(pkgName, step) }
                    // The installer hands back a StateFlow of the current step, which never ends on
                    // its own. Without stopping at the last step the collection would wait forever
                    // and the rest of the queue would never start.
                    .takeWhile { step -> !step.isCompleted() }
                    .collect()
            }

            if (finished == null) {
                logcat(LogPriority.WARN) { "Gave up waiting for $pkgName to finish installing" }
                setStep(pkgName, InstallStep.Error("Gave up waiting for the installer"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not install $pkgName" }
            setStep(pkgName, InstallStep.Error.from(e))
        }
    }

    private fun setStep(pkgName: String, step: InstallStep) {
        _state.update { state ->
            if (step != InstallStep.Installed) {
                return@update state.copy(installs = state.installs + (pkgName to step))
            }
            // Installed: it is not missing any more, so it leaves the list rather than sitting
            // there with a tick the user has to reason about.
            state.copy(
                items = state.items.filterNot { it.extension.pkgName == pkgName },
                installs = state.installs - pkgName,
                installed = state.installed + pkgName,
            )
        }
    }

    @Immutable
    data class Batch(val total: Int, val done: Int)

    @Immutable
    data class State(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val items: List<MissingExtension> = emptyList(),
        val installs: Map<String, InstallStep> = emptyMap(),
        val installed: Set<String> = emptySet(),
        val batch: Batch? = null,
    ) {
        val isInstalling: Boolean get() = batch != null || installs.values.any { !it.isCompleted() }
    }

    private companion object {
        /**
         * Safety net for an install that never reports back — a confirmation dialog left untouched,
         * say. Long enough not to cut short a slow download on mobile data.
         */
        const val INSTALL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
