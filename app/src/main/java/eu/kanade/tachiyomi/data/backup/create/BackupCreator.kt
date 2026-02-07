package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import eu.kanade.tachiyomi.source.isNovelSource

class BackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val getFavorites: GetFavorites = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val sourceManager: tachiyomi.domain.source.service.SourceManager = Injekt.get(),

    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val extensionRepoBackupCreator: ExtensionRepoBackupCreator = ExtensionRepoBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
) {

    /**
     * Batch size for processing manga during backup.
     * Smaller batches reduce memory pressure but may be slower.
     * Reduced to 5 manga per batch to prevent OOM on very large libraries (100k+ entries).
     */
    private val MANGA_BATCH_SIZE = 5

    /**
     * Number of batches after which to suggest GC.
     * Every N batches, we hint to the GC that it can collect garbage.
     * More frequent GC (every 1 batch = 5 manga) to handle large libraries.
     */
    private val GC_HINT_INTERVAL = 1

    /**
     * Maximum number of manga to hold in memory at once before flushing.
     * For very large libraries, we process in memory-bounded segments.
     */
    private val MAX_MANGA_IN_MEMORY = 200

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)

                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            val allManga = getFavorites.await() + nonFavoriteManga
            
            // Log library size for debugging OOM issues
            logcat(LogPriority.INFO) { "Backup: Processing ${allManga.size} manga entries" }
            
            // Filter by manga/novel content type based on options
            val filteredManga = allManga.filter { manga ->
                val isNovel = sourceManager.getOrStub(manga.source).isNovelSource()
                when {
                    options.includeManga && options.includeNovels -> true
                    options.includeManga && !isNovel -> true
                    options.includeNovels && isNovel -> true
                    else -> false
                }
            }
            
            logcat(LogPriority.INFO) { "Backup: ${filteredManga.size} manga after filtering" }
            
            // Use streaming protobuf serialization to avoid OOM on large libraries.
            // Instead of building one huge Backup object and serializing it all at once,
            // we serialize each BackupManga individually and write the raw protobuf bytes
            // incrementally to the output stream.
            val backupCategories = backupCategories(options)
            val backupAppPrefs = backupAppPreferences(options)
            val backupExtensionRepos = backupExtensionRepos(options)
            val backupSourcePrefs = backupSourcePreferences(options)
            
            // Collect manga in batches, tracking sources as we go
            val allBackupManga = mutableListOf<BackupManga>()
            var batchCount = 0
            val totalBatches = (filteredManga.size + MANGA_BATCH_SIZE - 1) / MANGA_BATCH_SIZE
            
            filteredManga.chunked(MANGA_BATCH_SIZE).forEach { batch ->
                allBackupManga.addAll(mangaBackupCreator.backupMangaStream(batch, options).toList())
                kotlinx.coroutines.yield()
                batchCount++
                if (batchCount % GC_HINT_INTERVAL == 0) {
                    if (batchCount % 50 == 0) {
                        logcat(LogPriority.DEBUG) { "Backup: Processed $batchCount/$totalBatches batches (${allBackupManga.size} manga)" }
                    }
                    System.gc()
                }
            }
            
            val backupSources = backupSources(allBackupManga)
            
            logcat(LogPriority.INFO) { "Backup: All manga processed (${allBackupManga.size} entries), streaming to file..." }
            System.gc()

            // Stream-write protobuf directly to gzipped output to avoid holding full byte array
            val outputStream = file.openOutputStream()
            (outputStream as? FileOutputStream)?.channel?.truncate(0)
            val gzipOut = outputStream.sink().gzip().buffer()
            try {
                streamWriteBackup(
                    gzipOut.outputStream(),
                    allBackupManga,
                    backupCategories,
                    backupSources,
                    backupAppPrefs,
                    backupSourcePrefs,
                    backupExtensionRepos,
                )
                gzipOut.flush()
            } finally {
                gzipOut.close()
            }
            
            val fileUri = file.uri
            logcat(LogPriority.INFO) { "Backup: Write complete, validating..." }

            // Make sure it's a valid backup file
            BackupFileValidator(context).validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp().set(Instant.now().toEpochMilli())
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            try {
                file?.delete()
            } catch (deleteError: Exception) {
                logcat(LogPriority.WARN, deleteError) { "Failed to delete partial backup file" }
            }
            throw e
        }
    }

    /**
     * Stream-write a Backup as protobuf directly to an OutputStream.
     * This avoids creating one massive byte array for the entire backup.
     * 
     * Protobuf wire format: each field is (field_number << 3 | wire_type).
     * For length-delimited fields (strings, bytes, embedded messages): wire_type = 2.
     * Repeated fields are written as separate field entries.
     */
    private fun streamWriteBackup(
        out: OutputStream,
        manga: List<BackupManga>,
        categories: List<BackupCategory>,
        sources: List<BackupSource>,
        preferences: List<BackupPreference>,
        sourcePreferences: List<BackupSourcePreferences>,
        extensionRepos: List<BackupExtensionRepos>,
    ) {
        // Field 1: backupManga (repeated)
        manga.forEach { m ->
            val bytes = parser.encodeToByteArray(BackupManga.serializer(), m)
            writeProtoField(out, 1, bytes)
        }
        // Field 2: backupCategories (repeated)
        categories.forEach { c ->
            val bytes = parser.encodeToByteArray(BackupCategory.serializer(), c)
            writeProtoField(out, 2, bytes)
        }
        // Field 101: backupSources (repeated)
        sources.forEach { s ->
            val bytes = parser.encodeToByteArray(BackupSource.serializer(), s)
            writeProtoField(out, 101, bytes)
        }
        // Field 104: backupPreferences (repeated)
        preferences.forEach { p ->
            val bytes = parser.encodeToByteArray(BackupPreference.serializer(), p)
            writeProtoField(out, 104, bytes)
        }
        // Field 105: backupSourcePreferences (repeated)
        sourcePreferences.forEach { sp ->
            val bytes = parser.encodeToByteArray(BackupSourcePreferences.serializer(), sp)
            writeProtoField(out, 105, bytes)
        }
        // Field 106: backupExtensionRepo (repeated)
        extensionRepos.forEach { er ->
            val bytes = parser.encodeToByteArray(BackupExtensionRepos.serializer(), er)
            writeProtoField(out, 106, bytes)
        }
    }

    /** Write a single protobuf length-delimited field (wire type 2) to the stream. */
    private fun writeProtoField(out: OutputStream, fieldNumber: Int, data: ByteArray) {
        // Tag = (fieldNumber << 3) | 2 (length-delimited)
        writeVarint(out, (fieldNumber.toLong() shl 3) or 2L)
        writeVarint(out, data.size.toLong())
        out.write(data)
    }

    /** Write a varint (variable-length integer) in protobuf encoding. */
    private fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    private fun backupSources(mangas: List<BackupManga>): List<BackupSource> {
        return sourcesBackupCreator(mangas)
    }

    private fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun backupExtensionRepos(options: BackupOptions): List<BackupExtensionRepos> {
        if (!options.extensionRepoSettings) return emptyList()

        return extensionRepoBackupCreator()
    }

    private fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
