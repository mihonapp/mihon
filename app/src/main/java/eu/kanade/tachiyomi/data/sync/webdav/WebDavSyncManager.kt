package eu.kanade.tachiyomi.data.sync.webdav

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.sync.service.WebDavSyncPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Clock

@Serializable
private data class LocalSyncBase(
    val snapshot: WebDavSyncSnapshot,
    val localInstalledExtensionPkgs: Set<String> = emptySet(),
)

@Inject
@SingleIn(AppScope::class)
class WebDavSyncManager(
    private val context: Context,
    private val webDavClient: WebDavClient,
    private val snapshotter: WebDavSyncSnapshotter,
    private val applier: WebDavSyncApplier,
    private val extensionManager: ExtensionManager,
    private val preferences: WebDavSyncPreferences,
) {
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val syncJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun currentConfig(): WebDavConfig = WebDavConfig(
        serverUrl = preferences.serverUrl.get().trim(),
        username = preferences.username.get(),
        password = preferences.password.get(),
        remotePath = preferences.remotePath.get().trim().ifBlank { "/mihon" },
    )

    fun currentOptions(): WebDavSyncOptions = WebDavSyncOptions(
        syncLibrary = preferences.syncLibrary.get(),
        syncExtensionStores = preferences.syncExtensionStores.get(),
        syncExtensions = preferences.syncExtensions.get(),
    )

    suspend fun testConnection(config: WebDavConfig = currentConfig()) {
        webDavClient.testConnection(config)
    }

    suspend fun sync(): WebDavSyncSummary = syncMutex.withLock {
        _isSyncing.value = true
        try {
            val config = currentConfig()
            require(config.serverUrl.isNotBlank()) { "WebDAV server URL is not configured" }

            val options = currentOptions()
            require(options.hasAnyEnabled()) { "No sync categories selected" }

            val localBase = readLocalBase()
            var finalSummary: WebDavSyncSummary? = null

            for (attempt in 0 until MAX_CONFLICT_RETRIES) {
                val remoteFile = webDavClient.download(config, REMOTE_SNAPSHOT_FILE)
                val remoteSnapshot = remoteFile?.let { decodeGzipJson<WebDavSyncSnapshot>(it.bytes) }

                val localSnapshot = snapshotter.createLocalSnapshot(options)
                val localInstalledPkgs = localSnapshot.installedExtensions.map { it.pkgName }.toSet()

                val merged = WebDavSyncMerger.merge(
                    base = localBase?.snapshot,
                    local = localSnapshot,
                    remote = remoteSnapshot,
                    options = options,
                    lastLocalInstalledExtensionPkgs = localBase?.localInstalledExtensionPkgs.orEmpty(),
                )

                val localChanged = applier.apply(localSnapshot, merged, options)

                val needsUpload = remoteSnapshot == null ||
                    merged.normalizedForComparison() != remoteSnapshot.normalizedForComparison()

                var newEtag = remoteFile?.etag
                if (needsUpload) {
                    // Re-check remote ETag before uploading to avoid clobbering concurrent writes from another device
                    if (remoteFile?.etag != null && attempt < MAX_CONFLICT_RETRIES - 1) {
                        val latestEtag = runCatching {
                            webDavClient.headEtag(config, REMOTE_SNAPSHOT_FILE)
                        }.getOrNull()
                        if (latestEtag != null && latestEtag != remoteFile.etag) {
                            logcat(LogPriority.INFO) { "WebDAV remote changed during merge, retrying sync" }
                            continue
                        }
                    }

                    val uploadPayload = merged.copy(
                        updatedAt = Clock.System.now().toEpochMilliseconds(),
                        deviceId = preferences.getOrCreateDeviceId(),
                    )
                    val compressed = encodeGzipJson(uploadPayload)
                    newEtag = webDavClient.upload(config, REMOTE_SNAPSHOT_FILE, compressed)
                }

                val missingExtensions = if (options.syncExtensions) {
                    val missing = merged.installedExtensions.filter { it.pkgName !in localInstalledPkgs }
                    saveMissingExtensions(missing)
                    if (missing.isNotEmpty() || (options.syncExtensionStores && localChanged)) {
                        runCatching { extensionManager.findAvailableExtensions() }
                            .onFailure {
                                logcat(LogPriority.WARN, it) { "Failed to refresh extension stores after sync" }
                            }
                    }
                    missing
                } else {
                    emptyList()
                }

                writeLocalBase(
                    LocalSyncBase(
                        snapshot = merged,
                        localInstalledExtensionPkgs = localInstalledPkgs,
                    ),
                )

                val now = Clock.System.now().toEpochMilliseconds()
                preferences.lastSyncTimestamp.set(now)
                if (!newEtag.isNullOrBlank()) {
                    preferences.lastSyncEtag.set(newEtag)
                }
                preferences.lastSyncStatus.set("")

                finalSummary = WebDavSyncSummary(
                    localChanged = localChanged,
                    remoteChanged = needsUpload,
                    missingExtensions = missingExtensions,
                )
                break
            }

            finalSummary ?: throw IllegalStateException("WebDAV sync conflicted repeatedly")
        } catch (e: Exception) {
            preferences.lastSyncStatus.set(e.message ?: e::class.simpleName.orEmpty())
            throw e
        } finally {
            _isSyncing.value = false
        }
    }

    fun getMissingExtensions(): List<SyncInstalledExtension> {
        val raw = preferences.missingExtensionsJson.get()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            syncJson.decodeFromString<List<SyncInstalledExtension>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun refreshMissingExtensions(): List<SyncInstalledExtension> {
        val currentMissing = getMissingExtensions()
        if (currentMissing.isEmpty()) return emptyList()
        val installedPkgs = snapshotter.getInstalledExtensionsSnapshot().map { it.pkgName }.toSet()
        val remaining = currentMissing.filter { it.pkgName !in installedPkgs }
        if (remaining.size != currentMissing.size) {
            saveMissingExtensions(remaining)
            // Update local installed package tracking in base so newly installed extensions count as locally owned
            readLocalBase()?.let { base ->
                writeLocalBase(
                    base.copy(localInstalledExtensionPkgs = base.localInstalledExtensionPkgs + installedPkgs),
                )
            }
        }
        return remaining
    }

    fun clearLocalBase() {
        baseSnapshotFile().delete()
        preferences.lastSyncEtag.set("")
        preferences.missingExtensionsJson.set("[]")
    }

    private fun saveMissingExtensions(missing: List<SyncInstalledExtension>) {
        preferences.missingExtensionsJson.set(syncJson.encodeToString(missing))
    }

    private fun baseSnapshotFile(): File = File(context.filesDir, BASE_SNAPSHOT_FILE)

    private suspend fun readLocalBase(): LocalSyncBase? = withContext(Dispatchers.IO) {
        val file = baseSnapshotFile()
        if (!file.exists()) return@withContext null
        runCatching {
            decodeGzipJson<LocalSyncBase>(file.readBytes())
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Failed to read local WebDAV sync baseline; falling back to union merge" }
        }.getOrNull()
    }

    private suspend fun writeLocalBase(base: LocalSyncBase) = withContext(Dispatchers.IO) {
        val target = baseSnapshotFile()
        val temp = File(context.filesDir, "$BASE_SNAPSHOT_FILE.tmp")
        temp.writeBytes(encodeGzipJson(base))
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private inline fun <reified T> encodeGzipJson(value: T): ByteArray {
        val jsonBytes = syncJson.encodeToString(value).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(jsonBytes) }
        return out.toByteArray()
    }

    private inline fun <reified T> decodeGzipJson(bytes: ByteArray): T {
        val plain = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        } else {
            bytes
        }
        return syncJson.decodeFromString(plain.toString(Charsets.UTF_8))
    }

    companion object {
        const val REMOTE_SNAPSHOT_FILE = "mihon_sync_v1.json.gz"
        private const val BASE_SNAPSHOT_FILE = "webdav_sync_base.json.gz"
        private const val MAX_CONFLICT_RETRIES = 3
    }
}
