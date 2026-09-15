package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.extension.builtin.BundledMangaDexSource
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsHttpSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** The value type used by desktop source preference screens. */
enum class SourcePreferenceType {
    Boolean,
    String,
    Int,
    Long,
    Float,
    Select,
    List,
    Unsupported,
}

/** A single option of a [SourcePreferenceType.Select] / [SourcePreferenceType.List] preference. */
data class SourcePreferenceOption(
    val label: String,
    val value: String,
)

/**
 * A single configurable field exposed by a source. Values are persisted through
 * [DesktopPreferenceStore] under a key scoped to the source id.
 *
 * [currentValue] is populated when definitions are loaded from the extension host; callers that
 * only have manifest/builtin definitions can leave it null and use [defaultValue].
 */
data class SourcePreferenceDefinition(
    val key: String,
    val title: String,
    val summary: String = "",
    val type: SourcePreferenceType = SourcePreferenceType.Boolean,
    val defaultValue: String = "",
    val options: List<SourcePreferenceOption> = emptyList(),
    val isReadOnly: Boolean = false,
    val currentValue: String? = null,
)

/** Result of [DesktopSourceManager.getSourcePreferencesSnapshot]. */
data class SourcePreferencesSnapshot(
    val definitions: List<SourcePreferenceDefinition> = emptyList(),
    val supported: Boolean = false,
)

/** Encodes a multi-select value for the desktop preference store / UI callback API. */
fun encodeSourcePreferenceListValue(values: Collection<String>): String = values.joinToString(",")

/** Decodes a multi-select value produced by [encodeSourcePreferenceListValue]. */
fun decodeSourcePreferenceListValue(value: String?): List<String> {
    if (value.isNullOrEmpty()) return emptyList()
    return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Desktop equivalent of Android's `ConfigurableSource`. A builtin source that implements this
 * interface can expose its settings to the desktop source preferences screen.
 *
 * Extension packages can opt into the same UI by declaring `source_preferences` (or
 * `configurable_source`) plus optional `pref:<type>:<key>:<title>` capability entries in their
 * manifest.
 */
interface DesktopConfigurableSource : WindowsCatalogueSource {
    fun getPreferenceDefinitions(): List<SourcePreferenceDefinition>
}

/**
 * A source together with the desktop-managed flags. [DesktopSourceManager.getSources] only returns
 * enabled sources; the details screen uses the richer state list to toggle individual sources.
 */
data class SourceState(
    val source: SourceDescriptor,
    val isEnabled: Boolean = true,
    val isIncognito: Boolean = false,
    val isConfigurable: Boolean = false,
    val extensionPackage: String? = null,
    val isLocal: Boolean = false,
)

class DesktopSourceManager(
    private val installer: DesktopExtensionInstaller? = null,
    val processManager: WindowsExtensionProcessManager? = null,
    preferenceStore: DesktopPreferenceStore? = null,
    private val cookieStore: DesktopCookieStore? = null,
    private var libraryRepository: LibraryRepository? = null,
    private var localLibraryRepository: LibraryRepository? = null,
    private var localMangaRepository: LibraryRepository? = null,
    private var repository: LibraryRepository? = null,
    private var localRepository: LibraryRepository? = null,
) : Closeable {

    companion object {
        const val PREF_PREFIX_SOURCE_ENABLED = "extension.source.enabled."
        const val PREF_PREFIX_SOURCE_INCOGNITO = "extension.source.incognito."
        const val PREF_PREFIX_EXTENSION_INCOGNITO = "extension.incognito."
        const val PREF_PREFIX_SOURCE_PREFERENCE = "extension.source.preference."

        const val CAPABILITY_SOURCE_PREFERENCES = "source_preferences"
        const val CAPABILITY_CONFIGURABLE_SOURCE = "configurable_source"
        const val CAPABILITY_PREFERENCE_PREFIX = "pref:"

        private val activeManagers = java.util.concurrent.CopyOnWriteArrayList<WeakReference<DesktopSourceManager>>()

        private fun registerActiveManager(manager: DesktopSourceManager) {
            activeManagers.removeAll { it.get() == null || it.get() === manager }
            activeManagers.add(WeakReference(manager))
        }

        private fun unregisterActiveManager(manager: DesktopSourceManager) {
            activeManagers.removeAll { it.get() == null || it.get() === manager }
        }

        /**
         * Finds the live manager responsible for [sourceId].
         *
         * The desktop Compose navigation only forwards definitions/values to
         * `SourcePreferencesScreen`; this registry lets the screen lazily fetch the authoritative
         * extension-host model without changing every navigation call site.
         */
        internal suspend fun releaseInstalledPackage(installer: DesktopExtensionInstaller, pkg: String) {
            activeManagers.mapNotNull { it.get() }.filter { it.installer === installer }.forEach { manager ->
                manager.sourceLoadMutex.withLock {
                    manager.processManager?.unloadExtension(pkg)
                    manager.unloadExtension(pkg)
                }
            }
        }

        internal suspend fun probeInstalledPackage(
            installer: DesktopExtensionInstaller,
            packageFile: java.io.File,
            pkg: String,
        ): List<SourceDescriptor> {
            val manager = activeManagers.mapNotNull { it.get() }
                .lastOrNull { it.installer === installer && it.processManager != null }
                ?: throw IllegalStateException(
                    "An extension host is required to discover original APK source identities",
                )
            return manager.sourceLoadMutex.withLock {
                try {
                    manager.processManager!!.loadExtension(packageFile)
                } finally {
                    manager.processManager!!.unloadExtension(pkg)
                    manager.unloadExtension(pkg)
                }
            }
        }

        fun findActiveManagerForSource(sourceId: Long): DesktopSourceManager? {
            val candidates = activeManagers
                .mapNotNull { it.get() }
                .filter { it.ownsSource(sourceId) }
            return candidates.lastOrNull { it.processManager != null } ?: candidates.lastOrNull()
        }
    }

    private val builtinSources = ConcurrentHashMap<Long, WindowsCatalogueSource>()
    private val registeredSourcePreferences = ConcurrentHashMap<Long, List<SourcePreferenceDefinition>>()
    private val preferenceStore: DesktopPreferenceStore? = preferenceStore ?: installer?.preferenceStore

    /** Definitions/current values last returned by the extension host for a source. */
    private val remoteSourcePreferences = ConcurrentHashMap<Long, List<SourcePreferenceDefinition>>()

    /** Source ids whose extension host response confirmed ConfigurableSource support. */
    private val remotePreferenceSupport = ConcurrentHashMap.newKeySet<Long>()

    /** Falls back to the runtime cookie store located next to the extension install root. */
    private val fallbackCookieStore: DesktopCookieStore? by lazy {
        val root = installer?.installRoot?.parentFile ?: return@lazy null
        DesktopCookieStore(root.resolve("cookies.json").toPath())
    }

    private val activeCookieStore: DesktopCookieStore?
        get() = cookieStore ?: fallbackCookieStore

    private fun effectiveLibraryRepository(): LibraryRepository? =
        libraryRepository ?: localLibraryRepository ?: localMangaRepository ?: repository ?: localRepository

    /** Built-in local source backed by the imported local manga rows in the desktop library. */
    val localSource: BundledLocalSource = BundledLocalSource { effectiveLibraryRepository() }

    init {
        registerActiveManager(this)
        // Register default out-of-the-box bundled sources
        val mangadex = BundledMangaDexSource()
        registerBuiltinSource(mangadex)
        registerBuiltinSource(localSource)
    }

    /**
     * Connects the local source to the runtime library repository. Production constructs the source
     * manager before the browse presenter, so the presenter wires the repository in when it starts.
     */
    fun attachLibraryRepository(repository: LibraryRepository?) {
        libraryRepository = repository
        localLibraryRepository = repository
        localMangaRepository = repository
        this.repository = repository
        localRepository = repository
    }

    /** Alias for [attachLibraryRepository]. */
    fun setLibraryRepository(repository: LibraryRepository?) = attachLibraryRepository(repository)

    /** Alias for [attachLibraryRepository] matching local-source terminology. */
    fun attachLocalLibraryRepository(repository: LibraryRepository?) = attachLibraryRepository(repository)

    /** Alias for [attachLibraryRepository] matching local-source terminology. */
    fun setLocalLibraryRepository(repository: LibraryRepository?) = attachLibraryRepository(repository)

    /** Alias for [attachLibraryRepository] matching local-source terminology. */
    fun registerLocalLibraryRepository(repository: LibraryRepository?) = attachLibraryRepository(repository)

    /** Alias for [attachLibraryRepository] matching local-source terminology. */
    fun registerLocalSource(repository: LibraryRepository?) = attachLibraryRepository(repository)

    fun registerBuiltinSource(source: WindowsCatalogueSource) {
        builtinSources[source.id] = source
        if (source is DesktopConfigurableSource) {
            registeredSourcePreferences[source.id] = source.getPreferenceDefinitions()
        }
    }

    fun unregisterBuiltinSource(sourceId: Long) {
        builtinSources.remove(sourceId)
        registeredSourcePreferences.remove(sourceId)
    }

    /** Registers preference definitions for a source that is not represented by a Kotlin object. */
    fun registerSourcePreferences(sourceId: Long, definitions: List<SourcePreferenceDefinition>) {
        registeredSourcePreferences[sourceId] = definitions
    }

    fun unregisterSourcePreferences(sourceId: Long) {
        registeredSourcePreferences.remove(sourceId)
    }

    /**
     * Returns every known source with its persisted desktop flags. Sources belonging to a disabled
     * extension are intentionally omitted, mirroring Android's extension enable toggle.
     */
    fun getSourceStates(): List<SourceState> {
        val builtins = builtinSources.values.map { source ->
            sourceState(descriptorFor(source), extensionPackage = null)
        }

        val extensionSources = installer?.getInstalledExtensions()
            ?.filter { it.isEnabled }
            ?.flatMap { extension ->
                extension.manifest.sources.map { source ->
                    sourceState(source, extensionPackage = extension.pkg)
                }
            }
            ?: emptyList()

        return (builtins + extensionSources).distinctBy { it.source.id }
    }

    /** Alias for [getSourceStates] matching the Android "sources with state" terminology. */
    fun getSourcesWithState(): List<SourceState> = getSourceStates()

    /** All sources reported by an installed extension, including sources disabled individually. */
    fun getSourceStatesForExtension(pkg: String): List<SourceState> {
        val extension = installer?.getInstalledExtensions()?.firstOrNull { it.pkg == pkg } ?: return emptyList()
        return extension.manifest.sources.map { source ->
            sourceState(source, extensionPackage = extension.pkg)
        }
    }

    /** Alias for [getSourceStatesForExtension]. */
    fun getSourcesForExtension(pkg: String): List<SourceState> = getSourceStatesForExtension(pkg)

    /** Current source list. Disabled sources (extension-level or per-source) are filtered out. */
    fun getSources(): List<SourceDescriptor> {
        return getSourceStates().filter { it.isEnabled }.map { it.source }
    }

    fun findSourceDescriptor(sourceId: Long): SourceDescriptor? {
        return getSources().find { it.id == sourceId }
    }

    // ---------------------------------------------------------------------
    // Per-source / per-extension desktop settings
    // ---------------------------------------------------------------------

    fun isSourceEnabled(sourceId: Long): Boolean = readBoolean(sourceEnabledKey(sourceId), default = true)

    fun setSourceEnabled(sourceId: Long, enabled: Boolean) {
        writeProperty(sourceEnabledKey(sourceId), enabled.toString())
    }

    fun toggleSourceEnabled(sourceId: Long): Boolean {
        val updated = !isSourceEnabled(sourceId)
        setSourceEnabled(sourceId, updated)
        return updated
    }

    fun isSourceIncognito(sourceId: Long): Boolean = readBoolean(sourceIncognitoKey(sourceId), default = false)

    fun setSourceIncognito(sourceId: Long, incognito: Boolean) {
        writeProperty(sourceIncognitoKey(sourceId), incognito.toString())
    }

    fun toggleSourceIncognito(sourceId: Long): Boolean {
        val updated = !isSourceIncognito(sourceId)
        setSourceIncognito(sourceId, updated)
        return updated
    }

    fun isExtensionIncognito(pkg: String): Boolean = readBoolean(extensionIncognitoKey(pkg), default = false)

    fun setExtensionIncognito(pkg: String, incognito: Boolean) {
        writeProperty(extensionIncognitoKey(pkg), incognito.toString())
    }

    fun toggleExtensionIncognito(pkg: String): Boolean {
        val updated = !isExtensionIncognito(pkg)
        setExtensionIncognito(pkg, updated)
        return updated
    }

    fun getSourcePreferenceValue(sourceId: Long, key: String): String? {
        remoteSourcePreferences[sourceId]?.find { it.key == key }?.currentValue?.let { return it }
        return getLocalSourcePreferenceValue(sourceId, key)
    }

    fun setSourcePreferenceValue(sourceId: Long, key: String, value: String) {
        writeProperty(sourcePreferenceKey(sourceId, key), value)
        remoteSourcePreferences[sourceId]?.let { definitions ->
            remoteSourcePreferences[sourceId] = definitions.map { definition ->
                if (definition.key == key) definition.copy(currentValue = value) else definition
            }
        }
    }

    /**
     * Async set path for real extension-host preferences. Builtin/manifest sources keep using the
     * local [DesktopPreferenceStore] fallback; sources backed by a ConfigurableSource also update
     * the live preference object in the extension host.
     */
    suspend fun setSourcePreference(sourceId: Long, key: String, value: String) {
        setSourcePreferenceInternal(sourceId, key, value)
    }

    suspend fun setSourcePreference(sourceId: Long, key: String, value: Boolean) =
        setSourcePreference(sourceId, key, value.toString())

    suspend fun setSourcePreference(sourceId: Long, key: String, value: Int) =
        setSourcePreference(sourceId, key, value.toString())

    suspend fun setSourcePreference(sourceId: Long, key: String, value: Long) =
        setSourcePreference(sourceId, key, value.toString())

    suspend fun setSourcePreference(sourceId: Long, key: String, value: Float) =
        setSourcePreference(sourceId, key, value.toString())

    suspend fun setSourcePreference(sourceId: Long, key: String, value: List<String>) =
        setSourcePreference(sourceId, key, encodeSourcePreferenceListValue(value))

    fun getSourcePreferenceBoolean(sourceId: Long, key: String, default: Boolean = false): Boolean {
        return getSourcePreferenceValue(sourceId, key)?.toBooleanStrictOrNull() ?: default
    }

    fun setSourcePreferenceBoolean(sourceId: Long, key: String, value: Boolean) {
        setSourcePreferenceValue(sourceId, key, value.toString())
    }

    /**
     * Asynchronously loads definitions (with current values) for [sourceId].
     *
     * Extension sources that implement `eu.kanade.tachiyomi.source.ConfigurableSource` are queried
     * through the extension host. Sources without ConfigurableSource support transparently fall
     * back to registered/manifest definitions and locally persisted values.
     */
    suspend fun getSourcePreferences(sourceId: Long): List<SourcePreferenceDefinition> =
        getSourcePreferencesSnapshot(sourceId).definitions

    suspend fun getSourcePreferencesSnapshot(sourceId: Long): SourcePreferencesSnapshot = withContext(Dispatchers.IO) {
        val remote = loadRemoteSourcePreferences(sourceId)
        if (remote != null && remote.supported) {
            remotePreferenceSupport.add(sourceId)
            remoteSourcePreferences[sourceId] = remote.definitions
            return@withContext remote
        }

        remotePreferenceSupport.remove(sourceId)
        remoteSourcePreferences.remove(sourceId)
        val fallback = fallbackSourcePreferenceDefinitions(sourceId).map { definition ->
            definition.copy(
                currentValue = getLocalSourcePreferenceValue(sourceId, definition.key)
                    ?: definition.defaultValue,
            )
        }
        SourcePreferencesSnapshot(
            definitions = fallback,
            supported = isLocallyConfigurable(sourceId) || fallback.isNotEmpty(),
        )
    }

    fun getSourcePreferenceDefinitions(sourceId: Long): List<SourcePreferenceDefinition> {
        remoteSourcePreferences[sourceId]?.let { return it }
        return fallbackSourcePreferenceDefinitions(sourceId)
    }

    fun isSourceConfigurable(sourceId: Long): Boolean {
        if (sourceId in remotePreferenceSupport) return true
        return isLocallyConfigurable(sourceId)
    }

    /** True when this manager can resolve [sourceId] (builtin or installed extension source). */
    fun ownsSource(sourceId: Long): Boolean {
        if (builtinSources.containsKey(sourceId)) return true
        return installer?.getInstalledExtensions()
            ?.any { extension -> extension.manifest.sources.any { it.id == sourceId } }
            ?: false
    }

    // ---------------------------------------------------------------------
    // Cookies
    // ---------------------------------------------------------------------

    /** Removes stored cookies for every domain declared by the extension containing [sourceId]. */
    fun clearSourceCookies(sourceId: Long): Int {
        val store = activeCookieStore ?: return 0
        val owner = installer?.getInstalledExtensions()
            ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }?.pkg ?: "builtin"
        store.clearSession(owner)
        var cleared = 0
        cookieDomainsForSource(sourceId).forEach { domain ->
            if (store.getDomainConfig(domain) != null) {
                store.removeCookies(domain)
                cleared++
            }
        }
        return cleared
    }

    fun clearExtensionCookies(pkg: String): Int {
        val extension = installer?.getInstalledExtensions()?.firstOrNull { it.pkg == pkg } ?: return 0
        return extension.manifest.sources.sumOf { source -> clearSourceCookies(source.id) }
    }

    /** Alias for [clearSourceCookies]. */
    fun clearCookies(sourceId: Long): Int = clearSourceCookies(sourceId)

    /** Alias for [clearExtensionCookies]. */
    fun clearCookiesForExtension(pkg: String): Int = clearExtensionCookies(pkg)

    private fun cookieDomainsForSource(sourceId: Long): Set<String> {
        val domains = linkedSetOf<String>()

        val extension = installer?.getInstalledExtensions()
            ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
        extension?.manifest?.declaredDomains?.forEach { domain ->
            if (domain.isNotBlank() && domain != "*") {
                domains += domain
            }
        }

        val builtin = builtinSources[sourceId]
        if (builtin is WindowsHttpSource) {
            runCatching { java.net.URI(builtin.baseUrl).host }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { domains += it }
        }

        return domains
    }

    // ---------------------------------------------------------------------
    // Source loading / browsing
    // ---------------------------------------------------------------------

    private val loadedPackages = ConcurrentHashMap.newKeySet<String>()
    private val sourceLoadMutex = Mutex()
    private var lastKnownEpoch: Long = -1L

    suspend fun ensureSourceLoaded(sourceId: Long) {
        if (builtinSources.containsKey(sourceId)) return
        if (installer != null) {
            installer.withLifecycleLock { sourceLoadMutex.withLock { loadSource(sourceId) } }
        } else {
            sourceLoadMutex.withLock { loadSource(sourceId) }
        }
    }

    suspend fun sourceWebPage(sourceId: Long): String? {
        (builtinSources[sourceId] as? WindowsHttpSource)?.let { return it.baseUrl }
        ensureSourceLoaded(sourceId)
        return processManager?.getSources()?.firstOrNull { it.id == sourceId }?.baseUrl
    }

    private suspend fun loadSource(sourceId: Long) {
        val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")

        // Starting the host can advance its epoch after a crash. Do this before consulting the
        // package cache so a restarted, empty host never inherits the previous process' loaded set.
        proc.start()

        // Reset loaded packages if extension host process was restarted
        if (proc.epoch != lastKnownEpoch) {
            loadedPackages.clear()
            remoteSourcePreferences.clear()
            remotePreferenceSupport.clear()
            lastKnownEpoch = proc.epoch
        }

        val installed = installer?.getInstalledExtensions() ?: emptyList()
        val targetExt = installed.firstOrNull { ext ->
            ext.isEnabled &&
                isSourceEnabled(sourceId) &&
                ext.manifest.sources.any { s -> s.id == sourceId }
        } ?: return

        // Suwayomi resolves its source cache first and reloads the owning extension on a miss.
        // Validate the host registry as well as this process-local package hint so a live host with
        // a cleared/partially replaced registry can repair itself without an application restart.
        val hostHasSource = if (loadedPackages.contains(targetExt.pkg)) {
            runCatching { proc.getSources().any { it.id == sourceId } }.getOrDefault(false)
        } else {
            false
        }
        if (!hostHasSource) {
            loadedPackages.remove(targetExt.pkg)
            val packageFile = java.io.File(targetExt.packageFile)
            if (packageFile.exists()) {
                val loadedSources = proc.loadExtension(packageFile)
                check(loadedSources.any { it.id == sourceId }) {
                    "Extension ${targetExt.pkg} loaded without requested source $sourceId " +
                        "(registered=${loadedSources.map { it.id }})"
                }
                // Publish the package hint only after every saved source setting has been restored.
                // Call IPC directly while holding the load mutex; recursively ensuring here deadlocks.
                loadedSources.forEach { source -> restoreSourcePreferences(proc, source.id) }
                loadedPackages.add(targetExt.pkg)
            }
        }
    }

    fun unloadExtension(pkg: String) {
        loadedPackages.remove(pkg)
        installer?.getInstalledExtensions()
            ?.firstOrNull { it.pkg == pkg }
            ?.manifest
            ?.sources
            ?.forEach { source ->
                remoteSourcePreferences.remove(source.id)
                remotePreferenceSupport.remove(source.id)
            }
    }

    suspend fun getPopular(sourceId: Long, page: Int): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getPopularManga(page)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.getPopular(sourceId, page) }
    }

    suspend fun getLatest(sourceId: Long, page: Int): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getLatestUpdates(page)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.getLatest(sourceId, page) }
    }

    fun getFilterList(sourceId: Long): FilterList {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return builtin.getFilterList()
        }
        return try {
            // Callers such as the desktop Compose source screen expect a synchronous filter list.
            // The process manager keeps the host/extension loaded, so subsequent calls are cheap.
            runBlocking { loadFilterList(sourceId) }
        } catch (_: Exception) {
            FilterList()
        }
    }

    /**
     * Asynchronously loads the filter list for a source. Builtin sources resolve immediately;
     * extension sources are loaded through the extension host and decoded from the IPC DTO.
     */
    suspend fun loadFilterList(sourceId: Long): FilterList = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getFilterList()
        }
        if (processManager == null) return@withContext FilterList()
        try {
            withLoadedExtensionSource(sourceId) { proc -> proc.getFilterList(sourceId) }
        } catch (_: Exception) {
            FilterList()
        }
    }

    suspend fun searchManga(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList = FilterList(),
    ): MangasPage = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.searchManga(page, query, filters)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.searchManga(sourceId, page, query, filters) }
    }

    suspend fun getMangaDetails(sourceId: Long, manga: SManga): SManga = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getMangaDetails(manga)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.getMangaDetails(sourceId, manga) }
    }

    suspend fun getChapterList(sourceId: Long, manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getChapterList(manga)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.getChapterList(sourceId, manga) }
    }

    suspend fun getPageList(sourceId: Long, chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext builtin.getPageList(chapter)
        }
        if (processManager == null) return@withContext emptyList()
        withLoadedExtensionSource(sourceId) { proc -> proc.getPageList(sourceId, chapter) }
    }

    suspend fun getImage(sourceId: Long, page: Page): ByteArray? = withContext(Dispatchers.IO) {
        val builtin = builtinSources[sourceId]
        if (builtin != null) {
            return@withContext (builtin as? mihon.extension.source.WindowsImageSource)?.getImage(page)
        }
        withLoadedExtensionSource(sourceId) { proc -> proc.getImage(sourceId, page) }
    }

    override fun close() {
        unregisterActiveManager(this)
        processManager?.close()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private suspend fun setSourcePreferenceInternal(sourceId: Long, key: String, value: String) {
        val cachedRemote = if (sourceId in remotePreferenceSupport) remoteSourcePreferences[sourceId] else null
        val definitions = cachedRemote ?: getSourcePreferencesSnapshot(sourceId).definitions
        val definition = definitions.find { it.key == key }
            ?: fallbackSourcePreferenceDefinitions(sourceId).find { it.key == key }
            ?: throw IllegalArgumentException("Unknown source preference '$key'")
        if (definition.isReadOnly || definition.type == SourcePreferenceType.Unsupported) {
            throw IllegalArgumentException("Source preference '$key' is read-only")
        }

        if (sourceId in remotePreferenceSupport && processManager != null) {
            val remoteValue = definition.type.toSourcePreferenceValueDto(value)
            withLoadedExtensionSource(sourceId) { proc ->
                proc.setSourcePreference(sourceId, key, remoteValue)
            }
                ?: throw IllegalStateException("Extension host did not return the updated source preference")
            remoteSourcePreferences[sourceId] = definitions.map { current ->
                if (current.key == key) current.copy(currentValue = value) else current
            }
        }

        writeProperty(sourcePreferenceKey(sourceId, key), value)
    }

    private suspend fun loadRemoteSourcePreferences(sourceId: Long): SourcePreferencesSnapshot? {
        if (builtinSources.containsKey(sourceId)) return null
        val proc = processManager ?: return null
        val installed = installer?.getInstalledExtensions() ?: return null
        val extension = installed.firstOrNull { candidate ->
            candidate.isEnabled && candidate.manifest.sources.any { it.id == sourceId }
        } ?: return null

        return try {
            val response = withLoadedExtensionSource(sourceId) { manager ->
                manager.getSourcePreferencesResult(sourceId)
            }
            val definitions = response.definitions.map { it.toDesktopDefinition() }
            SourcePreferencesSnapshot(
                definitions = definitions,
                supported = response.supported,
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun restoreSourcePreferences(proc: WindowsExtensionProcessManager, sourceId: Long) {
        val saved = preferenceStore?.propertiesWithPrefix("$PREF_PREFIX_SOURCE_PREFERENCE$sourceId.").orEmpty()
        if (saved.isEmpty()) return
        val response = proc.getSourcePreferencesResult(sourceId)
        if (!response.supported) return
        val definitions = response.definitions.map { it.toDesktopDefinition() }.map { definition ->
            val stored = saved[sourcePreferenceKey(sourceId, definition.key)]
            if (stored == null || definition.isReadOnly || definition.type == SourcePreferenceType.Unsupported) {
                definition
            } else {
                proc.setSourcePreference(sourceId, definition.key, definition.type.toSourcePreferenceValueDto(stored))
                definition.copy(currentValue = stored)
            }
        }
        remotePreferenceSupport.add(sourceId)
        remoteSourcePreferences[sourceId] = definitions
    }

    /**
     * Runs one extension-source operation against a registry that contains [sourceId]. The process
     * manager can transparently start a fresh host between validation and the request itself; that
     * new host has an empty source registry. Repair that narrow race by reloading the owner and
     * retrying only the host's explicit missing-source response, at most once.
     */
    private suspend fun <T> withLoadedExtensionSource(
        sourceId: Long,
        operation: suspend (WindowsExtensionProcessManager) -> T,
    ): T {
        val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")
        ensureSourceLoaded(sourceId)
        return try {
            operation(proc)
        } catch (error: IpcException) {
            if (!error.isMissingSource(sourceId)) throw error
            installer?.getInstalledExtensions()
                ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
                ?.let { extension -> loadedPackages.remove(extension.pkg) }
            ensureSourceLoaded(sourceId)
            operation(proc)
        }
    }

    private fun IpcException.isMissingSource(sourceId: Long): Boolean =
        message?.contains("Source with ID $sourceId not found", ignoreCase = true) == true

    private fun fallbackSourcePreferenceDefinitions(sourceId: Long): List<SourcePreferenceDefinition> {
        registeredSourcePreferences[sourceId]?.takeIf { it.isNotEmpty() }?.let { return it }

        val builtin = builtinSources[sourceId]
        if (builtin is DesktopConfigurableSource) {
            return builtin.getPreferenceDefinitions()
        }

        val extension = installer?.getInstalledExtensions()
            ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
            ?: return emptyList()

        return extension.manifest.capabilities.mapNotNull(::parseSourcePreferenceCapability)
    }

    private fun isLocallyConfigurable(sourceId: Long): Boolean {
        if (registeredSourcePreferences[sourceId]?.isNotEmpty() == true) return true
        if (builtinSources[sourceId] is DesktopConfigurableSource) return true

        val extension = installer?.getInstalledExtensions()
            ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
            ?: return false

        return extension.manifest.capabilities.any { capability ->
            capability.equals(CAPABILITY_SOURCE_PREFERENCES, ignoreCase = true) ||
                capability.equals(CAPABILITY_CONFIGURABLE_SOURCE, ignoreCase = true) ||
                capability.startsWith(CAPABILITY_PREFERENCE_PREFIX, ignoreCase = true)
        }
    }

    private fun getLocalSourcePreferenceValue(sourceId: Long, key: String): String? {
        return preferenceStore?.property(sourcePreferenceKey(sourceId, key))
    }

    private fun SourcePreferenceDefinitionDto.toDesktopDefinition(): SourcePreferenceDefinition {
        return SourcePreferenceDefinition(
            key = key,
            title = title.ifBlank { key },
            summary = summary,
            type = type.toDesktopType(),
            defaultValue = defaultValue.toDisplayString(),
            currentValue = (currentValue ?: defaultValue).toDisplayString(),
            options = options.map { SourcePreferenceOption(label = it.label, value = it.value) },
            isReadOnly = readOnly || type == SourcePreferenceTypeDto.UNKNOWN,
        )
    }

    private fun SourcePreferenceTypeDto.toDesktopType(): SourcePreferenceType = when (this) {
        SourcePreferenceTypeDto.BOOLEAN -> SourcePreferenceType.Boolean
        SourcePreferenceTypeDto.STRING -> SourcePreferenceType.String
        SourcePreferenceTypeDto.INT -> SourcePreferenceType.Int
        SourcePreferenceTypeDto.LONG -> SourcePreferenceType.Long
        SourcePreferenceTypeDto.FLOAT -> SourcePreferenceType.Float
        SourcePreferenceTypeDto.SELECT -> SourcePreferenceType.Select
        SourcePreferenceTypeDto.LIST -> SourcePreferenceType.List
        SourcePreferenceTypeDto.UNKNOWN -> SourcePreferenceType.Unsupported
    }

    private fun SourcePreferenceValueDto?.toDisplayString(): String = when (this) {
        null -> ""
        is BooleanPreferenceValueDto -> value.toString()
        is StringPreferenceValueDto -> value
        is IntPreferenceValueDto -> value.toString()
        is LongPreferenceValueDto -> value.toString()
        is FloatPreferenceValueDto -> value.toString()
        is SelectPreferenceValueDto -> value
        is ListPreferenceValueDto -> encodeSourcePreferenceListValue(value)
        is UnknownPreferenceValueDto -> value.orEmpty()
    }

    private fun SourcePreferenceType.toSourcePreferenceValueDto(raw: String): SourcePreferenceValueDto {
        return when (this) {
            SourcePreferenceType.Boolean -> BooleanPreferenceValueDto(
                raw.toBooleanStrictOrNull()
                    ?: when (raw.trim().lowercase()) {
                        "1" -> true
                        "0" -> false
                        else -> throw IllegalArgumentException("Expected a boolean value")
                    },
            )
            SourcePreferenceType.String -> StringPreferenceValueDto(raw)
            SourcePreferenceType.Int -> IntPreferenceValueDto(
                raw.toIntOrNull() ?: throw IllegalArgumentException("Expected an int value"),
            )
            SourcePreferenceType.Long -> LongPreferenceValueDto(
                raw.toLongOrNull() ?: throw IllegalArgumentException("Expected a long value"),
            )
            SourcePreferenceType.Float -> FloatPreferenceValueDto(
                raw.toFloatOrNull() ?: throw IllegalArgumentException("Expected a float value"),
            )
            SourcePreferenceType.Select -> SelectPreferenceValueDto(raw)
            SourcePreferenceType.List -> ListPreferenceValueDto(decodeSourcePreferenceListValue(raw))
            SourcePreferenceType.Unsupported -> throw IllegalArgumentException("Unsupported source preference type")
        }
    }

    private fun descriptorFor(source: WindowsCatalogueSource): SourceDescriptor = SourceDescriptor(
        id = source.id,
        name = source.name,
        lang = source.lang,
        className = source::class.java.name,
        supportsLatest = source.supportsLatest,
    )

    private fun sourceState(source: SourceDescriptor, extensionPackage: String?): SourceState = SourceState(
        source = source,
        isEnabled = isSourceEnabled(source.id),
        isIncognito = isSourceIncognito(source.id),
        isConfigurable = isSourceConfigurable(source.id),
        extensionPackage = extensionPackage,
        isLocal = source.id == BundledLocalSource.ID,
    )

    private fun sourceEnabledKey(sourceId: Long): String = "$PREF_PREFIX_SOURCE_ENABLED$sourceId"

    private fun sourceIncognitoKey(sourceId: Long): String = "$PREF_PREFIX_SOURCE_INCOGNITO$sourceId"

    private fun extensionIncognitoKey(pkg: String): String = "$PREF_PREFIX_EXTENSION_INCOGNITO$pkg"

    private fun sourcePreferenceKey(sourceId: Long, key: String): String {
        return "$PREF_PREFIX_SOURCE_PREFERENCE$sourceId.$key"
    }

    private fun readBoolean(key: String, default: Boolean): Boolean {
        return preferenceStore?.property(key)?.toBooleanStrictOrNull() ?: default
    }

    private fun writeProperty(key: String, value: String) {
        preferenceStore?.update {
            setProperty(key, value)
        }
    }

    private fun parseSourcePreferenceCapability(capability: String): SourcePreferenceDefinition? {
        if (!capability.startsWith(CAPABILITY_PREFERENCE_PREFIX, ignoreCase = true)) return null
        val parts = capability.split(":")
        if (parts.size < 4) return null

        val type = when (parts[1].trim().lowercase()) {
            "boolean", "bool" -> SourcePreferenceType.Boolean
            "string", "text" -> SourcePreferenceType.String
            "int", "integer" -> SourcePreferenceType.Int
            "long" -> SourcePreferenceType.Long
            "float", "double" -> SourcePreferenceType.Float
            "select" -> SourcePreferenceType.Select
            "list", "multiselect", "multi" -> SourcePreferenceType.List
            else -> return null
        }

        val key = parts[2].trim()
        if (key.isEmpty()) return null

        val title = parts.drop(3).joinToString(":").trim().ifBlank { key }
        return SourcePreferenceDefinition(
            key = key,
            title = title,
            type = type,
        )
    }
}
