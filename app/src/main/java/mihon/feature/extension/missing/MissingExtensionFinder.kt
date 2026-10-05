package mihon.feature.extension.missing

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mihon.domain.extension.repository.ExtensionStoreRepository
import mihon.sync.SyncPreferences
import mihon.sync.model.SyncExtensionRegistry
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

/**
 * Works out which extensions this device is missing, and which one would rescue a given source.
 *
 * The answer comes from the repositories' catalogues rather than from anything the sync invented:
 * an extension is only ever offered because a repository the user configured is offering it.
 */
@Inject
@SingleIn(AppScope::class)
class MissingExtensionFinder(
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val extensionManager: ExtensionManager,
    private val sourceManager: SourceManager,
    private val getFavorites: GetFavorites,
    private val syncPreferences: SyncPreferences,
    private val json: Json,
) {

    private val catalogueMutex = Mutex()
    private var catalogue: List<Extension.Available> = emptyList()
    private var catalogueFetchedAt = 0L

    /**
     * The extension that would make [sourceId] usable, or null when the source already works or no
     * configured repository offers it.
     */
    suspend fun forSource(sourceId: Long): Extension.Available? {
        if (sourceManager.get(sourceId) != null) return null

        val installed = installedPackages()
        return catalogue()
            .firstOrNull { extension -> extension.sources.any { it.id == sourceId } }
            ?.takeIf { it.pkgName !in installed }
    }

    /**
     * Everything worth offering, entries the library actually needs first.
     */
    suspend fun findAll(refresh: Boolean = false): List<MissingExtension> {
        val available = catalogue(refresh)
        if (available.isEmpty()) return emptyList()

        val installed = installedPackages()
        val found = LinkedHashMap<String, MissingExtension>()

        // Sources the library refers to but this device cannot reach. Those are what actually
        // break: an entry with no cover, and an error instead of a reader.
        for ((sourceId, count) in libraryUsage()) {
            if (sourceManager.get(sourceId) != null) continue

            val extension = available.firstOrNull { it.sources.any { source -> source.id == sourceId } } ?: continue
            if (extension.pkgName in installed) continue

            val sourceName = extension.sources.first { it.id == sourceId }.name
            val known = found[extension.pkgName]
            found[extension.pkgName] = known
                ?.copy(entryCount = known.entryCount + count, sourceNames = known.sourceNames + sourceName)
                ?: MissingExtension(extension, entryCount = count, sourceNames = listOf(sourceName))
        }

        // Extensions another device has that nothing here needs yet. Offered without urgency, so
        // a device that was deliberately kept light stays light until the user says otherwise.
        for (info in syncedExtensions()) {
            if (info.pkgName in installed || info.pkgName in found) continue
            val extension = available.firstOrNull { it.pkgName == info.pkgName } ?: continue
            found[info.pkgName] = MissingExtension(extension, entryCount = 0, sourceNames = emptyList())
        }

        return found.values.sortedWith(
            compareByDescending<MissingExtension> { it.entryCount }.thenBy { it.extension.name },
        )
    }

    private suspend fun libraryUsage(): Map<Long, Int> {
        val counts = LinkedHashMap<Long, Int>()
        for (manga in getFavorites.await()) {
            counts[manga.source] = (counts[manga.source] ?: 0) + 1
        }
        return counts
    }

    private suspend fun installedPackages(): Set<String> =
        extensionManager.getLoadedExtensions().mapTo(mutableSetOf()) { it.pkgName }

    private fun syncedExtensions() =
        runCatching { json.decodeFromString<SyncExtensionRegistry>(syncPreferences.knownExtensions().get()) }
            .getOrNull()
            ?.extensions
            .orEmpty()

    /**
     * The repositories' catalogues, fetched over the network and held briefly.
     *
     * Short-lived on purpose: the dialog and the list both ask, often seconds apart, and neither is
     * worth a second round trip — but a catalogue held any longer would keep offering an extension
     * the user has just installed.
     */
    private suspend fun catalogue(refresh: Boolean = false): List<Extension.Available> = catalogueMutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val isFresh = catalogue.isNotEmpty() && now - catalogueFetchedAt < CATALOGUE_TTL_MS
        if (isFresh && !refresh) return@withLock catalogue

        catalogue = withIOContext { extensionStoreRepository.fetchExtensions() }
        catalogueFetchedAt = now
        catalogue
    }

    private companion object {
        const val CATALOGUE_TTL_MS = 60_000L
    }
}
