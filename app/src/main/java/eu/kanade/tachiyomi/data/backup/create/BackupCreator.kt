package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags.BACKUP_APP_PREFS
import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags.BACKUP_CATEGORY
import eu.kanade.tachiyomi.data.backup.create.BackupCreateFlags.BACKUP_SOURCE_PREFS
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSerializer
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream

class BackupCreator(
    private val context: Context,
    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    private val getFavorites: GetFavorites = Injekt.get(),
) {

    internal val parser = ProtoBuf

    /**
     * Create backup file.
     *
     * @param uri path of Uri
     * @param isAutoBackup backup called from scheduled backup job
     */
    suspend fun createBackup(uri: Uri, flags: Int, isAutoBackup: Boolean): String {
        var file: UniFile? = null
        try {
            file = (
                if (isAutoBackup) {
                    // Get dir of file and create
                    val dir = UniFile.fromUri(context, uri)

                    // Delete older backups
                    dir?.listFiles { _, filename -> Backup.filenameRegex.matches(filename) }
                        .orEmpty()
                        .sortedByDescending { it.name }
                        .drop(MAX_AUTO_BACKUPS - 1)
                        .forEach { it.delete() }

                    // Create new file to place backup
                    dir?.createFile(Backup.getFilename())
                } else {
                    UniFile.fromUri(context, uri)
                }
                )
                ?: throw Exception(context.stringResource(MR.strings.create_backup_file_error))

            if (!file.isFile) {
                throw IllegalStateException("Failed to get handle on a backup file")
            }

            val databaseManga = getFavorites.await()
            val backup = Backup(
                backupManga = backupMangas(databaseManga, flags),
                backupCategories = backupCategories(flags),
                backupSources = backupSources(databaseManga),
                backupPreferences = backupAppPreferences(flags),
                backupSourcePreferences = backupSourcePreferences(flags),
            )

            val byteArray = parser.encodeToByteArray(BackupSerializer, backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            file.openOutputStream().also {
                // Force overwrite old file
                (it as? FileOutputStream)?.channel?.truncate(0)
            }.sink().gzip().buffer().use { it.write(byteArray) }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            BackupFileValidator().validate(context, fileUri)

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    private suspend fun backupCategories(options: Int): List<BackupCategory> {
        if (options and BACKUP_CATEGORY != BACKUP_CATEGORY) return emptyList()

        return categoriesBackupCreator.backupCategories()
    }

    private suspend fun backupMangas(mangas: List<Manga>, flags: Int): List<BackupManga> {
        return mangaBackupCreator.backupMangas(mangas, flags)
    }

    private fun backupSources(mangas: List<Manga>): List<BackupSource> {
        return sourcesBackupCreator.backupSources(mangas)
    }

    private fun backupAppPreferences(flags: Int): List<BackupPreference> {
        if (flags and BACKUP_APP_PREFS != BACKUP_APP_PREFS) return emptyList()

        return preferenceBackupCreator.backupAppPreferences()
    }

    private fun backupSourcePreferences(flags: Int): List<BackupSourcePreferences> {
        if (flags and BACKUP_SOURCE_PREFS != BACKUP_SOURCE_PREFS) return emptyList()

        return preferenceBackupCreator.backupSourcePreferences()
    }
}

private val MAX_AUTO_BACKUPS: Int = 4
