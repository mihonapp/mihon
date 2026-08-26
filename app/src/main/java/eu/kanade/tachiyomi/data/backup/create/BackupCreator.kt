package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.BufferedSink
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock

@AssistedInject
class BackupCreator(
    @Assisted private val isAutoBackup: Boolean,
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val mangaRepository: MangaRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val mangaBackupCreator: MangaBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val backupFileValidator: BackupFileValidator,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(isAutoBackup: Boolean): BackupCreator
    }

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
            val mangas = getFavorites.await() + nonFavoriteManga
            val backupMangaFlow = backupMangas(mangas, options)

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use { sink ->
                    val success = writeBackupToSink(
                        mangaFlow = backupMangaFlow,
                        categories = backupCategories(options),
                        sources = backupSources(mangas),
                        preferences = backupAppPreferences(options),
                        extensionStores = backupExtensionStores(options),
                        sourcePreferences = backupSourcePreferences(options),
                        sink = sink,
                    )

                    if (!success) {
                        throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
                    }
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            backupFileValidator.validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(Clock.System.now().toEpochMilliseconds())
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    private suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    private fun backupMangas(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> {
        if (!options.libraryEntries) return flowOf()

        return mangaBackupCreator(mangas, options)
    }

    private fun backupSources(mangas: List<Manga>): List<BackupSource> {
        return sourcesBackupCreator(mangas)
    }

    private fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    private fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun writeBackupToSink(
        mangaFlow: Flow<BackupManga>,
        categories: List<BackupCategory>,
        sources: List<BackupSource>,
        preferences: List<BackupPreference>,
        sourcePreferences: List<BackupSourcePreferences>,
        extensionStores: List<BackupExtensionStore>,
        sink: BufferedSink,
    ): Boolean {
        var emptyMangas = true

        val tempBuffer = okio.Buffer()

        mangaFlow.chunked(100).collect { chunk ->
            emptyMangas = false
            for (manga in chunk) {
                val mangaBytes = parser.encodeToByteArray(BackupManga.serializer(), manga)
                // Protobuf Tag for field 1, wire type 2 (Length-delimited): (1 << 3) | 2
                tempBuffer.writeByte(0x0A)
                tempBuffer.writeVarInt(mangaBytes.size)
                tempBuffer.write(mangaBytes)
            }

            sink.write(tempBuffer, tempBuffer.size)
        }

        val remaining = parser.encodeToByteArray(
            Backup.serializer(),
            Backup(
                backupManga = emptyList(),
                backupCategories = categories,
                backupSources = sources,
                backupPreferences = preferences,
                backupSourcePreferences = sourcePreferences,
                backupExtensionStores = extensionStores,
            ),
        )
        sink.write(remaining)

        return !emptyMangas || remaining.isNotEmpty()
    }

    private fun BufferedSink.writeVarInt(value: Int) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            writeByte(((v and 0x7F) or 0x80))
            v = v ushr 7
        }
        writeByte(v)
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
