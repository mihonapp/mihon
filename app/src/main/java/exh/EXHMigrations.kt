package exh

import android.content.Context
import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import exh.source.BlacklistedSources
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import uy.kohesive.injekt.injectLazy

object EXHMigrations {
    private val db: DatabaseHelper by injectLazy()

    private val logger = XLog.tag("EXHMigrations")

    /**
     * Performs a migration when the application is updated.
     *
     * @param preferences Preferences of the application.
     * @return true if a migration is performed, false otherwise.
     */
    fun upgrade(preferences: PreferencesHelper): Boolean {
        val context = preferences.context
        val oldVersion = preferences.eh_lastVersionCode().getOrDefault()
        try {
            if (oldVersion < BuildConfig.VERSION_CODE) {
                // if (oldVersion < 1) { }
                // do stuff here when releasing changed crap

                // TODO BE CAREFUL TO NOT FUCK UP MergedSources IF CHANGING URLs

                preferences.eh_lastVersionCode().set(BuildConfig.VERSION_CODE)

                return true
            }
        } catch (e: Exception) {
            logger.e("Failed to migrate app from $oldVersion -> ${BuildConfig.VERSION_CODE}!", e)
        }
        return false
    }

    fun migrateBackupEntry(backupEntry: BackupEntry): BackupEntry {
        val (manga, chapters, categories, history, tracks) = backupEntry

        // Migrate HentaiCafe source IDs
        if (manga.source == 6908L) {
            manga.source = HENTAI_CAFE_SOURCE_ID
        }

        // Migrate Tsumino source IDs
        if (manga.source == 6909L) {
            manga.source = TSUMINO_SOURCE_ID
        }

        // Migrate nhentai URLs
        if (manga.source == NHENTAI_SOURCE_ID) {
            manga.url = getUrlWithoutDomain(manga.url)
        }

        // Allow importing of nhentai extension backups
        if (manga.source in BlacklistedSources.NHENTAI_EXT_SOURCES) {
            manga.source = NHENTAI_SOURCE_ID
        }

        // Allow importing of English PervEden extension backups
        if (manga.source in BlacklistedSources.PERVEDEN_EN_EXT_SOURCES) {
            manga.source = PERV_EDEN_EN_SOURCE_ID
        }

        // Allow importing of Italian PervEden extension backups
        if (manga.source in BlacklistedSources.PERVEDEN_IT_EXT_SOURCES) {
            manga.source = PERV_EDEN_IT_SOURCE_ID
        }

        // Allow importing of EHentai extension backups
        if (manga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            manga.source = EH_SOURCE_ID
        }

        return backupEntry
    }

    private fun backupDatabase(context: Context, oldMigrationVersion: Int) {
        val backupLocation = File(File(context.filesDir, "exh_db_bck"), "$oldMigrationVersion.bck.db")
        if (backupLocation.exists()) return // Do not backup same version twice

        val dbLocation = context.getDatabasePath(db.lowLevel().sqliteOpenHelper().databaseName)
        try {
            dbLocation.copyTo(backupLocation, overwrite = true)
        } catch (t: Throwable) {
            XLog.w("Failed to backup database!")
        }
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }
}

data class BackupEntry(
    val manga: Manga,
    val chapters: List<Chapter>,
    val categories: List<String>,
    val history: List<DHistory>,
    val tracks: List<Track>
)
