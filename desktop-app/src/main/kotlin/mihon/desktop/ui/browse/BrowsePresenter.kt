package mihon.desktop.ui.browse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.extension.DesktopExtensionInstaller
import mihon.desktop.extension.DesktopSourceManager
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionStoreService
import mihon.desktop.library.db.SqlDelightLibraryRepository
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.SManga
import java.io.File

class BrowsePresenter(
    private val sourceManager: DesktopSourceManager,
    private val installer: DesktopExtensionInstaller,
    private val storeService: ExtensionStoreService,
    private val libraryRepository: SqlDelightLibraryRepository,
    private val preferenceStore: DesktopPreferenceStore,
    private val scope: CoroutineScope,
) {
    companion object {
        const val PREF_KEY_PINNED_SOURCES = "browse.pinned_sources"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        loadPinnedSources()
        refresh()
    }

    private fun loadPinnedSources() {
        val stored = preferenceStore.property(PREF_KEY_PINNED_SOURCES) ?: ""
        val ids = stored.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
        _state.update { it.copy(pinnedSourceIds = ids) }
    }

    private fun savePinnedSources(ids: Set<Long>) {
        preferenceStore.update {
            setProperty(PREF_KEY_PINNED_SOURCES, ids.joinToString(","))
        }
        _state.update { it.copy(pinnedSourceIds = ids) }
    }

    fun togglePinSource(sourceId: Long) {
        val current = _state.value.pinnedSourceIds
        val updated = if (current.contains(sourceId)) {
            current - sourceId
        } else {
            current + sourceId
        }
        savePinnedSources(updated)
    }

    fun setTab(tab: BrowseTab) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == BrowseTab.Migration) {
            refreshMigrationCounts()
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val repos = storeService.getRepositories()
                val installed = installer.getInstalledExtensions()
                val sources = sourceManager.getSources()

                // Fetch available extensions from configured repositories
                val available = mutableListOf<ExtensionStoreItem>()
                for (repo in repos) {
                    try {
                        val items = storeService.fetchRepository(repo)
                        available.addAll(items)
                    } catch (_: Exception) {
                        // Tolerate single repo failure
                    }
                }

                _state.update {
                    it.copy(
                        repositories = repos,
                        installedExtensions = installed,
                        availableExtensions = available.distinctBy { item -> item.pkg },
                        sources = sources,
                        isLoading = false,
                    )
                }
                refreshMigrationCounts()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to refresh: ${e.message}",
                    )
                }
            }
        }
    }

    fun refreshMigrationCounts() {
        scope.launch {
            try {
                val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
                val countsBySource = allManga
                    .groupBy { it.sourceId }
                    .mapValues { it.value.size }

                val sourceList = _state.value.sources
                val sourceMap = sourceList.associateBy { it.id }

                val counts = countsBySource.map { (sourceId, count) ->
                    SourceWithMangaCount(
                        sourceId = sourceId,
                        sourceName = sourceMap[sourceId]?.name ?: "Source #$sourceId",
                        mangaCount = count,
                    )
                }.sortedByDescending { it.mangaCount }

                _state.update { it.copy(sourcesWithMangaCounts = counts) }
            } catch (_: Exception) {}
        }
    }

    fun selectMigrationSource(source: SourceWithMangaCount?) {
        if (source == null) {
            _state.update {
                it.copy(selectedMigrationSource = null, mangasForSelectedMigrationSource = emptyList())
            }
            return
        }

        scope.launch {
            val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
            val mangas = allManga.filter { it.sourceId == source.sourceId }
            _state.update {
                it.copy(
                    selectedMigrationSource = source,
                    mangasForSelectedMigrationSource = mangas,
                )
            }
        }
    }

    suspend fun searchTargetMigrationSource(targetSourceId: Long, query: String): List<SManga> {
        return try {
            val page = sourceManager.searchManga(targetSourceId, 1, query)
            page.mangas
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun performMigration(
        oldManga: LibraryManga,
        targetSource: SourceDescriptor,
        targetManga: SManga,
    ) {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val detailedTarget = try {
                        sourceManager.getMangaDetails(targetSource.id, targetManga)
                    } catch (_: Exception) {
                        targetManga
                    }

                    val targetChapters = try {
                        sourceManager.getChapterList(targetSource.id, detailedTarget)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val oldChapters = libraryRepository.chapterSnapshot(oldManga.id)

                    libraryRepository.transaction {
                        val now = System.currentTimeMillis()
                        // 1. Insert new target manga if not exists
                        val existing = libraryRepository.findManga(targetSource.id, detailedTarget.url)
                        val newMangaId = if (existing == null) {
                            libraryRepository.insertManga(
                                MangaRecord(
                                    id = 0L,
                                    sourceId = targetSource.id,
                                    url = detailedTarget.url,
                                    title = detailedTarget.title,
                                    artist = detailedTarget.artist,
                                    author = detailedTarget.author,
                                    description = detailedTarget.description,
                                    genreJson = json.encodeToString(detailedTarget.genre),
                                    status = detailedTarget.status.toLong(),
                                    thumbnailUrl = detailedTarget.thumbnailUrl,
                                    favorite = true,
                                    dateAdded = now,
                                    lastModifiedAt = now,
                                    favoriteModifiedAt = now,
                                    initialized = true,
                                ),
                            )
                        } else {
                            libraryRepository.updateManga(existing.copy(favorite = true, favoriteModifiedAt = now))
                            existing.id
                        }

                        // 2. Map old chapter progress to new chapters
                        val newChapterRecords = targetChapters.mapIndexed { index, sc ->
                            val match = oldChapters.find { oc ->
                                (sc.chapterNumber > 0f && oc.chapterNumber == sc.chapterNumber.toDouble()) ||
                                    oc.name.trim().equals(sc.name.trim(), ignoreCase = true)
                            }

                            ChapterRecord(
                                id = 0L,
                                mangaId = newMangaId,
                                url = sc.url,
                                name = sc.name,
                                scanlator = sc.scanlator,
                                read = match?.read ?: false,
                                bookmark = match?.bookmark ?: false,
                                lastPageRead = match?.lastPageRead ?: 0L,
                                chapterNumber = sc.chapterNumber.toDouble(),
                                sourceOrder = index.toLong(),
                                dateFetch = now,
                                dateUpload = sc.dateUpload,
                                lastModifiedAt = now,
                            )
                        }

                        for (record in newChapterRecords) {
                            libraryRepository.insertChapter(record)
                        }

                        // 3. Mark old manga as unfavorited
                        val oldRecord = libraryRepository.findManga(oldManga.sourceId, oldManga.url)
                        if (oldRecord != null) {
                            libraryRepository.updateManga(oldRecord.copy(favorite = false))
                        }
                    }
                }

                refresh()
                _state.value.selectedMigrationSource?.let { selectMigrationSource(it) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Migration failed: ${e.message}") }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun installExtension(item: ExtensionStoreItem) {
        scope.launch {
            _state.update { it.copy(isInstalling = true, installingPkg = item.pkg) }
            try {
                if (item.downloadUrl.isNotBlank()) {
                    installer.downloadAndInstall(item.downloadUrl, item.sha256, item.repoUrl)
                }
                val updatedInstalled = installer.getInstalledExtensions()
                val updatedSources = sourceManager.getSources()
                _state.update {
                    it.copy(
                        installedExtensions = updatedInstalled,
                        sources = updatedSources,
                        isInstalling = false,
                        installingPkg = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isInstalling = false,
                        installingPkg = null,
                        errorMessage = "Failed to install ${item.name}: ${e.message}",
                    )
                }
            }
        }
    }

    fun installFromFile(file: File) {
        scope.launch {
            _state.update { it.copy(isInstalling = true, installingPkg = file.name) }
            try {
                installer.installFromLocalFile(file)
                val updatedInstalled = installer.getInstalledExtensions()
                val updatedSources = sourceManager.getSources()
                _state.update {
                    it.copy(
                        installedExtensions = updatedInstalled,
                        sources = updatedSources,
                        isInstalling = false,
                        installingPkg = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isInstalling = false,
                        installingPkg = null,
                        errorMessage = "Failed to install from file ${file.name}: ${e.message}",
                    )
                }
            }
        }
    }

    fun uninstallExtension(pkg: String) {
        scope.launch {
            try {
                installer.uninstall(pkg)
                val updatedInstalled = installer.getInstalledExtensions()
                val updatedSources = sourceManager.getSources()
                _state.update {
                    it.copy(
                        installedExtensions = updatedInstalled,
                        sources = updatedSources,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to uninstall $pkg: ${e.message}") }
            }
        }
    }

    fun toggleExtensionEnabled(pkg: String, enabled: Boolean) {
        scope.launch {
            try {
                installer.setExtensionEnabled(pkg, enabled)
                val updatedInstalled = installer.getInstalledExtensions()
                val updatedSources = sourceManager.getSources()
                _state.update {
                    it.copy(
                        installedExtensions = updatedInstalled,
                        sources = updatedSources,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to toggle $pkg: ${e.message}") }
            }
        }
    }

    fun addRepository(repoUrl: String) {
        storeService.addRepository(repoUrl)
        refresh()
    }

    fun removeRepository(repoUrl: String) {
        storeService.removeRepository(repoUrl)
        refresh()
    }

    fun updateAllPending() {
        val installedMap = _state.value.installedExtensions.associateBy { it.pkg }
        val toUpdate = _state.value.availableExtensions.filter { available ->
            val inst = installedMap[available.pkg]
            inst != null && available.versionCode > inst.manifest.versionCode
        }

        scope.launch {
            for (item in toUpdate) {
                installExtension(item)
            }
        }
    }

    fun openGlobalSearch() {
        _state.update {
            it.copy(
                isGlobalSearchOpen = true,
                globalSearchQuery = it.searchQuery,
            )
        }
        if (_state.value.searchQuery.isNotBlank()) {
            performGlobalSearch()
        }
    }

    fun closeGlobalSearch() {
        _state.update { it.copy(isGlobalSearchOpen = false) }
    }

    fun setGlobalSearchQuery(query: String) {
        _state.update { it.copy(globalSearchQuery = query) }
    }

    fun performGlobalSearch() {
        val query = _state.value.globalSearchQuery.trim()
        if (query.isBlank()) return

        val sources = _state.value.sources
        _state.update {
            it.copy(
                isGlobalSearching = true,
                globalSearchResults = sources.map { s -> GlobalSearchSourceResult(source = s, isLoading = true) },
            )
        }

        for (source in sources) {
            scope.launch {
                try {
                    val page = sourceManager.searchManga(source.id, 1, query)
                    _state.update { current ->
                        val updated = current.globalSearchResults.map { item ->
                            if (item.source.id == source.id) {
                                item.copy(isLoading = false, mangas = page.mangas)
                            } else {
                                item
                            }
                        }
                        val stillLoading = updated.any { it.isLoading }
                        current.copy(
                            globalSearchResults = updated,
                            isGlobalSearching = stillLoading,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { current ->
                        val updated = current.globalSearchResults.map { item ->
                            if (item.source.id == source.id) {
                                item.copy(isLoading = false, errorMessage = e.message ?: "Failed to search")
                            } else {
                                item
                            }
                        }
                        val stillLoading = updated.any { it.isLoading }
                        current.copy(
                            globalSearchResults = updated,
                            isGlobalSearching = stillLoading,
                        )
                    }
                }
            }
        }
    }
}
