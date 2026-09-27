package eu.kanade.tachiyomi.extension

import android.content.Context
import android.graphics.drawable.Drawable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * The manager of extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 */
@Inject
@SingleIn(AppScope::class)
class ExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences,
    private val trustExtension: TrustExtension,
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val updateExtensionStores: UpdateExtensionStores,
    private val installer: ExtensionInstaller,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
) {

    val scope = CoroutineScope(SupervisorJob())

    private val initialized = CompletableDeferred<Unit>()

    private val iconMap = mutableMapOf<String, Drawable>()

    private val loadedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Loaded>())
    val loadedExtensionsFlow = loadedExtensionMapFlow.mapExtensionsWhenInitialized()

    // Every store's listing, since more than one store can list the same extension
    private val availableExtensionListFlow = MutableStateFlow(emptyList<Extension.Available>())
    val availableExtensionsFlow = availableExtensionListFlow
        .map { extensions -> extensions.associateBy { it.pkgName }.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val notLoadedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.NotLoaded>())
    val notLoadedExtensionsFlow = notLoadedExtensionMapFlow.mapExtensionsWhenInitialized()

    init {
        scope.launch(Dispatchers.IO) {
            loadExtensions()
            ExtensionInstallReceiver(InstallationListener()).register(context)

            // Everything the load decision rests on can change while running, so decide again
            merge(
                trustExtension.changes(),
                preferences.enabledContentWarnings.changes().distinctUntilChanged().drop(1).map {},
                preferences.applyContentWarningsToInstalled.changes().distinctUntilChanged().drop(1).map {},
            )
                .collectLatest { loadExtensions() }
        }
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages.isSet()

    suspend fun getLoadedExtensions(): List<Extension.Loaded> {
        initialized.await()
        return loadedExtensionMapFlow.value.values.toList()
    }

    suspend fun getNotLoadedExtensions(): List<Extension.NotLoaded> {
        initialized.await()
        return notLoadedExtensionMapFlow.value.values.toList()
    }

    suspend fun getExtensionPackage(sourceId: Long): String? {
        return getLoadedExtensions().find { extension ->
            extension.sources.any { it.id == sourceId }
        }
            ?.pkgName
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return loadedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }
                ?.pkgName
        }
    }

    suspend fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = getExtensionPackage(sourceId) ?: return null

        return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
            ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo!!
                .loadIcon(context.packageManager)
        }
    }

    private var availableExtensionsSourcesData: Map<Long, StubSource> = emptyMap()

    private fun setupAvailableExtensionsSourcesDataMap(extensions: List<Extension.Available>) {
        if (extensions.isEmpty()) return
        availableExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableExtensionsSourcesData[id]

    /**
     * Loads and registers the installed extensions. Safe to call again: every extension is judged
     * again, so one can move between loaded and not loaded in either direction, while extensions
     * that still pass keep the instances they already had.
     */
    private suspend fun loadExtensions() {
        try {
            val extensions = ExtensionLoader.loadExtensions(context, loadedExtensionMapFlow.value)

            loadedExtensionMapFlow.value = extensions
                .filterIsInstance<Extension.Loaded>()
                .associateBy { it.pkgName }

            notLoadedExtensionMapFlow.value = extensions
                .filterIsInstance<Extension.NotLoaded>()
                .associateBy { it.pkgName }

            // Newly loaded extensions have no status derived from the store index yet
            refreshStatuses()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load extensions" }
        } finally {
            // Release anything waiting on the extensions whether or not the load worked
            initialized.complete(Unit)
        }
    }

    /**
     * Finds the available extensions in the stores and updates [availableExtensionListFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<Extension.Available> = try {
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.toast(MR.strings.extension_api_error) }
            return
        }

        setAvailableExtensions(extensions)
    }

    /**
     * Refreshes the stores and their listings like [findAvailableExtensions], then notifies about the
     * installed extensions that have an update. Stays silent when the stores can't be reached.
     */
    suspend fun checkForUpdates() {
        val extensions = try {
            updateExtensionStores()
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return
        }

        initialized.await()
        setAvailableExtensions(extensions)

        val names = (loadedExtensionMapFlow.value.values + notLoadedExtensionMapFlow.value.values)
            .filter { it.hasUpdate }
            .map { it.name }
        if (names.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(names)
        }
    }

    private suspend fun fetchExtensions(): List<Extension.Available> {
        return withIOContext { extensionStoreRepository.fetchExtensions() }
    }

    private fun setAvailableExtensions(extensions: List<Extension.Available>) {
        enableAdditionalSubLanguages(extensions)

        availableExtensionListFlow.value = extensions
        refreshStatuses()
        setupAvailableExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(extensions: List<Extension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the extension level.
        val availableLanguages = extensions
            .flatMap(Extension.Available::sources)
            .distinctBy(Extension.Available.Source::lang)
            .map(Extension.Available.Source::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages.defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages.set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be installed.
     */
    fun installExtension(extension: Extension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension)
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be updated.
     */
    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(availableExtensionListFlow.value) ?: return emptyFlow()
        val isUpdateForPrivatelyInstalled = !extension.isShared
        return installer.downloadAndInstall(update, isUpdateForPrivatelyInstalled)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        installer.cancelInstall(extension.pkgName)
    }

    /**
     * Sets to "installing" status of an extension installation.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    /**
     * Uninstalls the extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: Extension.Installed) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param extension the extension to trust
     */
    fun trust(extension: Extension.NotLoaded) {
        val reason = extension.reason as? Extension.NotLoaded.Reason.Untrusted ?: return
        notLoadedExtensionMapFlow.value[extension.pkgName] ?: return

        // Loading it again is left to the reload triggered by the trust change
        trustExtension.trust(extension.pkgName, extension.versionCode, reason.signatureHash)
    }

    /**
     * Registers the given extension in this and the source managers.
     *
     * @param extension The extension to be registered.
     */
    private fun registerExtension(extension: Extension.Loaded) {
        loadedExtensionMapFlow.value += extension
    }

    /**
     * Unregisters the extension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterExtension(pkgName: String) {
        loadedExtensionMapFlow.value -= pkgName
        notLoadedExtensionMapFlow.value -= pkgName
    }

    /**
     * Listener which receives events of the extensions being installed, updated or removed.
     */
    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override fun onExtensionLoaded(extension: Extension.Loaded) {
            registerExtension(extension)
            notLoadedExtensionMapFlow.value -= extension.pkgName
            refreshStatuses()
        }

        override fun onExtensionNotLoaded(extension: Extension.NotLoaded) {
            loadedExtensionMapFlow.value -= extension.pkgName
            notLoadedExtensionMapFlow.value += extension
            refreshStatuses()
        }

        override fun onPackageUninstalled(pkgName: String) {
            ExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * Derives what the store listings say about every installed extension, loaded or not. Without any
     * listing there's nothing to derive from, so they're left as they are rather than marked obsolete.
     */
    private fun refreshStatuses() {
        val available = availableExtensionListFlow.value
        if (available.isNotEmpty()) {
            loadedExtensionMapFlow.value = loadedExtensionMapFlow.value.mapValues { (_, extension) ->
                val listing = extension.findListing(available)
                extension.copy(
                    hasUpdate = extension.findUpdate(available) != null,
                    isObsolete = listing == null,
                )
            }
            notLoadedExtensionMapFlow.value = notLoadedExtensionMapFlow.value.mapValues { (_, extension) ->
                extension.copy(hasUpdate = extension.findUpdate(available) != null)
            }
        }
        updatePendingUpdatesCount()
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = (loadedExtensionMapFlow.value.values + notLoadedExtensionMapFlow.value.values)
            .count { it.hasUpdate }
        preferences.extensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            extensionUpdateNotifier.dismiss()
        }
    }

    private operator fun <T : Extension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    /**
     * Extensions are loaded in the background, so this flow only starts emitting once that finished.
     */
    private fun <T : Extension> StateFlow<Map<String, T>>.mapExtensionsWhenInitialized(): Flow<List<T>> {
        return onStart { initialized.await() }.map { it.values.toList() }
    }
}
