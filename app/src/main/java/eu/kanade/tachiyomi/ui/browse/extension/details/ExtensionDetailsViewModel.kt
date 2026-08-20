package eu.kanade.tachiyomi.ui.browse.extension.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.extension.interactor.ExtensionSourceItem
import eu.kanade.domain.extension.interactor.GetExtensionSources
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class ExtensionDetailsViewModel(
    @Assisted pkgName: String,
    private val context: Context,
    private val network: NetworkHelper,
    private val extensionManager: ExtensionManager,
    private val getExtensionSources: GetExtensionSources,
    private val toggleSource: ToggleSource,
    private val toggleIncognito: ToggleIncognito,
    private val preferences: SourcePreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(pkgName: String): ExtensionDetailsViewModel
    }

    val state: StateFlow<State> = extensionManager.installedExtensionsFlow
        .map { it.firstOrNull { extension -> extension.pkgName == pkgName } }
        .distinctUntilChanged()
        .flatMapLatest { extension ->
            if (extension == null) return@flatMapLatest flowOf(State.Uninstalled)
            combine(
                subscribeToSources(extension),
                preferences.incognitoExtensions.changes().map { pkgName in it }.distinctUntilChanged(),
            ) { sources, isIncognito ->
                State.Success(extension = extension, isIncognito = isIncognito, sources = sources)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State.Loading)

    private val successState: State.Success?
        get() = state.value as? State.Success

    private fun subscribeToSources(extension: Extension.Installed): Flow<List<ExtensionSourceItem>> {
        return getExtensionSources.subscribe(extension)
            .map {
                it.sortedWith(
                    compareBy(
                        { !it.enabled },
                        { item ->
                            item.source.name.takeIf { item.labelAsName }
                                ?: LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase()
                        },
                    ),
                )
            }
            .catch { throwable ->
                logcat(LogPriority.ERROR, throwable)
                emit(listOf())
            }
    }

    fun clearCookies() {
        val extension = successState?.extension ?: return

        val urls = extension.sources
            .filterIsInstance<HttpSource>()
            .flatMap { listOf(it.baseUrl, it.getHomeUrl()) }
            .filter { it.isNotEmpty() }
            .distinct()

        val cleared = urls.sumOf {
            try {
                network.cookieJar.remove(it.toHttpUrl())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to clear cookies for $it" }
                0
            }
        }

        logcat { "Cleared $cleared cookies for: ${urls.joinToString()}" }
    }

    fun uninstallExtension() {
        val extension = successState?.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        successState?.extension?.sources
            ?.map { it.id }
            ?.let { toggleSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        successState?.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    sealed interface State {

        data object Loading : State

        data object Uninstalled : State

        @Immutable
        data class Success(
            val extension: Extension.Installed,
            val isIncognito: Boolean,
            val sources: List<ExtensionSourceItem>,
        ) : State
    }
}
