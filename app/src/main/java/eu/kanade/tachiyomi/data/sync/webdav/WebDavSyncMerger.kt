package eu.kanade.tachiyomi.data.sync.webdav

import kotlin.math.max

/**
 * Pure 3-way merge engine for WebDAV sync snapshots.
 *
 * When [base] is null (first sync on a device), merges conservatively without deleting data from either side.
 * When [base] is present, single-side changes (including unfavoriting, unreading, removing bookmarks/categories/stores)
 * win over the unchanged side, while concurrent edits are resolved deterministically.
 */
object WebDavSyncMerger {

    fun merge(
        base: WebDavSyncSnapshot?,
        local: WebDavSyncSnapshot,
        remote: WebDavSyncSnapshot?,
        options: WebDavSyncOptions,
        lastLocalInstalledExtensionPkgs: Set<String> = emptySet(),
    ): WebDavSyncSnapshot {
        if (remote == null) {
            return local.normalizedForComparison()
        }

        val mergedCategories = if (options.syncLibrary) {
            mergeCategories(base?.categories, local.categories, remote.categories)
        } else {
            remote.categories
        }
        val validCategoryNames = mergedCategories.map { it.name }.toSet()

        val mergedManga = if (options.syncLibrary) {
            mergeMangaList(
                base = base?.manga,
                local = local.manga,
                remote = remote.manga,
                validCategoryNames = validCategoryNames,
            )
        } else {
            remote.manga
        }

        val mergedSources = if (options.syncLibrary) {
            mergeSources(local.sources, remote.sources, mergedManga)
        } else {
            remote.sources
        }

        val mergedStores = if (options.syncExtensionStores) {
            mergeExtensionStores(base?.extensionStores, local.extensionStores, remote.extensionStores)
        } else {
            remote.extensionStores
        }

        val mergedExtensions = if (options.syncExtensions) {
            mergeInstalledExtensions(
                base = base?.installedExtensions,
                local = local.installedExtensions,
                remote = remote.installedExtensions,
                lastLocalInstalledPkgs = lastLocalInstalledExtensionPkgs,
            )
        } else {
            remote.installedExtensions
        }

        return WebDavSyncSnapshot(
            schemaVersion = WebDavSyncSnapshot.CURRENT_SCHEMA_VERSION,
            updatedAt = max(local.updatedAt, remote.updatedAt),
            deviceId = local.deviceId,
            categories = mergedCategories,
            manga = mergedManga,
            sources = mergedSources,
            extensionStores = mergedStores,
            installedExtensions = mergedExtensions,
        ).normalizedForComparison()
    }

    private fun mergeCategories(
        base: List<SyncCategory>?,
        local: List<SyncCategory>,
        remote: List<SyncCategory>,
    ): List<SyncCategory> {
        val baseMap = base?.associateBy { it.name }
        val localMap = local.associateBy { it.name }
        val remoteMap = remote.associateBy { it.name }
        val allNames = linkedSetOf<String>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val merged = mutableListOf<SyncCategory>()
        for (name in allNames) {
            val b = baseMap?.get(name)
            val l = localMap[name]
            val r = remoteMap[name]
            val chosen = when {
                l != null && r != null -> when {
                    b != null && l == b -> r
                    b != null && r == b -> l
                    else -> l
                }
                l != null -> when {
                    b != null && l == b -> null // Deleted remotely
                    else -> l
                }
                r != null -> when {
                    b != null && r == b -> null // Deleted locally
                    else -> r
                }
                else -> null
            }
            if (chosen != null) {
                merged += chosen
            }
        }

        return merged
            .sortedWith(compareBy<SyncCategory> { it.order }.thenBy { it.name })
            .mapIndexed { index, category -> category.copy(order = index.toLong()) }
    }

    private fun mergeMangaList(
        base: List<SyncManga>?,
        local: List<SyncManga>,
        remote: List<SyncManga>,
        validCategoryNames: Set<String>,
    ): List<SyncManga> {
        val baseMap = base?.associateBy { it.key() }
        val localMap = local.associateBy { it.key() }
        val remoteMap = remote.associateBy { it.key() }
        val allKeys = linkedSetOf<SyncMangaKey>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val result = mutableListOf<SyncManga>()
        for (key in allKeys) {
            val b = baseMap?.get(key)?.normalized()
            val l = localMap[key]?.normalized()
            val r = remoteMap[key]?.normalized()

            val merged = when {
                l != null && r != null -> mergeManga(b, l, r, validCategoryNames)
                l != null -> when {
                    b != null && l == b -> null // Removed / unfavorited on remote
                    else -> l.copy(categories = l.categories.filter { it in validCategoryNames })
                }
                r != null -> when {
                    b != null && r == b -> null // Removed / unfavorited locally
                    else -> r.copy(categories = r.categories.filter { it in validCategoryNames })
                }
                else -> null
            }

            if (merged != null && shouldRetainManga(merged)) {
                result += merged.normalized()
            }
        }
        return result
    }

    private fun shouldRetainManga(manga: SyncManga): Boolean {
        return manga.favorite ||
            manga.chapters.any { it.read || it.bookmark || it.lastPageRead > 0 } ||
            manga.history.any { it.readAt != null }
    }

    private fun mergeManga(
        base: SyncManga?,
        local: SyncManga,
        remote: SyncManga,
        validCategoryNames: Set<String>,
    ): SyncManga {
        val localMaxRead = local.history.mapNotNull { it.readAt }.maxOrNull() ?: 0L
        val remoteMaxRead = remote.history.mapNotNull { it.readAt }.maxOrNull() ?: 0L
        val remoteNewer = remoteMaxRead > localMaxRead

        val favoriteAt = mergeFavoriteAt(base?.favoriteAt, local.favoriteAt, remote.favoriteAt)

        val localMetaChanged = base != null && !sameMetadata(local, base)
        val remoteMetaChanged = base != null && !sameMetadata(remote, base)
        val metaSource = when {
            base != null && localMetaChanged && !remoteMetaChanged -> local
            base != null && !localMetaChanged && remoteMetaChanged -> remote
            local.initialized && !remote.initialized -> local
            !local.initialized && remote.initialized -> remote
            remoteNewer -> remote
            else -> local
        }

        val mergedHistory = mergeHistory(base?.history, local.history, remote.history)
        val mergedChapters = mergeChapters(
            base = base?.chapters,
            local = local.chapters,
            remote = remote.chapters,
            localHistory = local.history.associateBy { it.chapterUrl },
            remoteHistory = remote.history.associateBy { it.chapterUrl },
        )
        val mergedTracks = mergeTracks(base?.tracks, local.tracks, remote.tracks)
        val mergedCategories = mergeStringSet(base?.categories, local.categories, remote.categories)
            .filter { it in validCategoryNames }
        val mergedScanlators = mergeStringSet(
            base?.excludedScanlators,
            local.excludedScanlators,
            remote.excludedScanlators,
        )

        return SyncManga(
            source = local.source,
            url = local.url,
            title = metaSource.title.ifBlank { local.title.ifBlank { remote.title } },
            artist = metaSource.artist,
            author = metaSource.author,
            description = metaSource.description,
            genre = metaSource.genre,
            status = metaSource.status,
            thumbnailUrl = metaSource.thumbnailUrl,
            updateStrategy = metaSource.updateStrategy,
            initialized = local.initialized || remote.initialized,
            favoriteAt = favoriteAt,
            viewerFlags = pickThreeWay(base?.viewerFlags, local.viewerFlags, remote.viewerFlags, remoteNewer),
            chapterFlags = pickThreeWay(base?.chapterFlags, local.chapterFlags, remote.chapterFlags, remoteNewer),
            notes = pickThreeWay(base?.notes, local.notes, remote.notes, remoteNewer),
            categories = mergedCategories,
            excludedScanlators = mergedScanlators,
            chapters = mergedChapters,
            history = mergedHistory,
            tracks = mergedTracks,
        )
    }

    private fun mergeFavoriteAt(base: Long?, local: Long?, remote: Long?): Long? {
        if (local == remote) return local
        if (base != null) {
            val localChanged = local != base
            val remoteChanged = remote != base
            if (localChanged && !remoteChanged) return local
            if (!localChanged && remoteChanged) return remote
        }
        return if (local != null || remote != null) {
            listOfNotNull(local, remote).filter { it > 0L }.minOrNull() ?: 0L
        } else {
            null
        }
    }

    private fun sameMetadata(a: SyncManga, b: SyncManga): Boolean {
        return a.title == b.title &&
            a.artist == b.artist &&
            a.author == b.author &&
            a.description == b.description &&
            a.genre == b.genre &&
            a.status == b.status &&
            a.thumbnailUrl == b.thumbnailUrl &&
            a.updateStrategy == b.updateStrategy &&
            a.initialized == b.initialized
    }

    private fun mergeChapters(
        base: List<SyncChapter>?,
        local: List<SyncChapter>,
        remote: List<SyncChapter>,
        localHistory: Map<String, SyncHistory>,
        remoteHistory: Map<String, SyncHistory>,
    ): List<SyncChapter> {
        val baseMap = base?.associateBy { it.url }
        val localMap = local.associateBy { it.url }
        val remoteMap = remote.associateBy { it.url }
        val allUrls = linkedSetOf<String>().apply {
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        return allUrls.mapNotNull { url ->
            val b = baseMap?.get(url)
            val l = localMap[url]
            val r = remoteMap[url]
            when {
                l != null && r != null -> mergeChapter(b, l, r, localHistory[url]?.readAt, remoteHistory[url]?.readAt)
                l != null -> l
                r != null -> r
                else -> null
            }
        }
    }

    private fun mergeChapter(
        base: SyncChapter?,
        local: SyncChapter,
        remote: SyncChapter,
        localReadAt: Long?,
        remoteReadAt: Long?,
    ): SyncChapter {
        val baseProgress = base?.let { Triple(it.read, it.bookmark, it.lastPageRead) }
        val localProgress = Triple(local.read, local.bookmark, local.lastPageRead)
        val remoteProgress = Triple(remote.read, remote.bookmark, remote.lastPageRead)

        val (read, bookmark, lastPageRead) = when {
            localProgress == remoteProgress -> localProgress
            baseProgress != null && localProgress == baseProgress -> remoteProgress
            baseProgress != null && remoteProgress == baseProgress -> localProgress
            localReadAt != null && remoteReadAt != null && localReadAt != remoteReadAt -> {
                val winner = if (remoteReadAt > localReadAt) remote else local
                val mergedBookmark = when {
                    base != null && local.bookmark == base.bookmark -> remote.bookmark
                    base != null && remote.bookmark == base.bookmark -> local.bookmark
                    else -> local.bookmark || remote.bookmark
                }
                Triple(winner.read, mergedBookmark, winner.lastPageRead)
            }
            else -> Triple(
                local.read || remote.read,
                local.bookmark || remote.bookmark,
                max(local.lastPageRead, remote.lastPageRead),
            )
        }

        val meta = if (local.dateFetch >= remote.dateFetch) local else remote
        return meta.copy(
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = max(local.dateFetch, remote.dateFetch),
        )
    }

    private fun mergeHistory(
        base: List<SyncHistory>?,
        local: List<SyncHistory>,
        remote: List<SyncHistory>,
    ): List<SyncHistory> {
        val baseMap = base?.associateBy { it.chapterUrl }
        val localMap = local.associateBy { it.chapterUrl }
        val remoteMap = remote.associateBy { it.chapterUrl }
        val allUrls = linkedSetOf<String>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val merged = mutableListOf<SyncHistory>()
        for (url in allUrls) {
            val b = baseMap?.get(url)
            val l = localMap[url]
            val r = remoteMap[url]
            val chosen = when {
                l != null && r != null -> {
                    val readAt = when {
                        l.readAt == r.readAt -> l.readAt
                        b != null && l.readAt == b.readAt -> r.readAt
                        b != null && r.readAt == b.readAt -> l.readAt
                        else -> listOfNotNull(l.readAt, r.readAt).maxOrNull()
                    }
                    val duration = if (b != null) {
                        val baseDur = b.readDuration
                        when {
                            l == b -> r.readDuration
                            r == b -> l.readDuration
                            else ->
                                baseDur +
                                    max(0L, l.readDuration - baseDur) +
                                    max(0L, r.readDuration - baseDur)
                        }
                    } else {
                        max(l.readDuration, r.readDuration)
                    }
                    SyncHistory(chapterUrl = url, readAt = readAt, readDuration = duration)
                }
                l != null -> if (b != null && l == b) null else l
                r != null -> if (b != null && r == b) null else r
                else -> null
            }
            if (chosen != null) {
                merged += chosen
            }
        }
        return merged
    }

    private fun mergeTracks(
        base: List<SyncTrack>?,
        local: List<SyncTrack>,
        remote: List<SyncTrack>,
    ): List<SyncTrack> {
        val baseMap = base?.associateBy { it.trackerId }
        val localMap = local.associateBy { it.trackerId }
        val remoteMap = remote.associateBy { it.trackerId }
        val allIds = linkedSetOf<Long>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val merged = mutableListOf<SyncTrack>()
        for (id in allIds) {
            val b = baseMap?.get(id)
            val l = localMap[id]
            val r = remoteMap[id]
            val chosen = when {
                l != null && r != null -> when {
                    b != null && l == b -> r
                    b != null && r == b -> l
                    r.lastChapterRead > l.lastChapterRead -> r
                    else -> l
                }
                l != null -> if (b != null && l == b) null else l
                r != null -> if (b != null && r == b) null else r
                else -> null
            }
            if (chosen != null) {
                merged += chosen
            }
        }
        return merged
    }

    private fun mergeSources(
        local: List<SyncSource>,
        remote: List<SyncSource>,
        mergedManga: List<SyncManga>,
    ): List<SyncSource> {
        val usedSourceIds = mergedManga.map { it.source }.toSet()
        val combined = LinkedHashMap<Long, SyncSource>()
        remote.forEach { if (it.sourceId in usedSourceIds && it.name.isNotBlank()) combined[it.sourceId] = it }
        local.forEach { if (it.sourceId in usedSourceIds && it.name.isNotBlank()) combined[it.sourceId] = it }
        return combined.values.toList()
    }

    private fun mergeExtensionStores(
        base: List<SyncExtensionStore>?,
        local: List<SyncExtensionStore>,
        remote: List<SyncExtensionStore>,
    ): List<SyncExtensionStore> {
        val baseMap = base?.associateBy { it.indexUrl }
        val localMap = local.associateBy { it.indexUrl }
        val remoteMap = remote.associateBy { it.indexUrl }
        val allUrls = linkedSetOf<String>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val merged = mutableListOf<SyncExtensionStore>()
        for (url in allUrls) {
            val b = baseMap?.get(url)
            val l = localMap[url]
            val r = remoteMap[url]
            val chosen = when {
                l != null && r != null -> when {
                    b != null && l == b -> r
                    b != null && r == b -> l
                    else -> l
                }
                l != null -> if (b != null && l == b) null else l
                r != null -> if (b != null && r == b) null else r
                else -> null
            }
            if (chosen != null) {
                merged += chosen
            }
        }
        return merged
    }

    private fun mergeInstalledExtensions(
        base: List<SyncInstalledExtension>?,
        local: List<SyncInstalledExtension>,
        remote: List<SyncInstalledExtension>,
        lastLocalInstalledPkgs: Set<String>,
    ): List<SyncInstalledExtension> {
        val baseMap = base?.associateBy { it.pkgName }
        val localMap = local.associateBy { it.pkgName }
        val remoteMap = remote.associateBy { it.pkgName }
        val allPkgs = linkedSetOf<String>().apply {
            baseMap?.keys?.let(::addAll)
            addAll(localMap.keys)
            addAll(remoteMap.keys)
        }

        val merged = mutableListOf<SyncInstalledExtension>()
        for (pkg in allPkgs) {
            val b = baseMap?.get(pkg)
            val l = localMap[pkg]
            val r = remoteMap[pkg]
            val chosen = when {
                l != null && r != null -> if (r.versionCode > l.versionCode) r else l
                l != null -> when {
                    b != null && l.pkgName == b.pkgName && r == null -> null // Uninstalled remotely
                    else -> l
                }
                r != null -> when {
                    // Only treat as locally uninstalled if this device actually had it installed previously.
                    // Pending/missing extensions that were never installed here remain in the synced list.
                    b != null && pkg in lastLocalInstalledPkgs -> null
                    else -> r
                }
                else -> null
            }
            if (chosen != null) {
                merged += chosen
            }
        }
        return merged
    }

    private fun mergeStringSet(
        base: List<String>?,
        local: List<String>,
        remote: List<String>,
    ): List<String> {
        val baseSet = base?.toSet()
        val localSet = local.toSet()
        val remoteSet = remote.toSet()
        val all = linkedSetOf<String>().apply {
            baseSet?.let(::addAll)
            addAll(localSet)
            addAll(remoteSet)
        }

        return all.filter { item ->
            val inLocal = item in localSet
            val inRemote = item in remoteSet
            when {
                inLocal && inRemote -> true
                inLocal -> baseSet == null || item !in baseSet
                inRemote -> baseSet == null || item !in baseSet
                else -> false
            }
        }.sorted()
    }

    private fun <T> pickThreeWay(base: T?, local: T, remote: T, remoteNewer: Boolean): T {
        if (local == remote) return local
        if (base != null) {
            if (local == base) return remote
            if (remote == base) return local
        }
        return if (remoteNewer) remote else local
    }
}
