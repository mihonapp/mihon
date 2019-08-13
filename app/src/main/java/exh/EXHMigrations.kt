package exh

import android.content.Context
import android.os.Build
import com.elvishew.xlog.XLog
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.resolvers.MangaUrlPutResolver
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.jobScheduler
import exh.source.BlacklistedSources
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.net.URI
import java.net.URISyntaxException

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
                if (oldVersion < 1) {
                    db.inTransaction {
                        // Migrate HentaiCafe source IDs
                        db.lowLevel().executeSQL(RawQuery.builder()
                                .query("""
                            UPDATE ${MangaTable.TABLE}
                                SET ${MangaTable.COL_SOURCE} = $HENTAI_CAFE_SOURCE_ID
                                WHERE ${MangaTable.COL_SOURCE} = 6908
                        """.trimIndent())
                                .affectsTables(MangaTable.TABLE)
                                .build())

                        // Migrate nhentai URLs
                        val nhentaiManga = db.db.get()
                                .listOfObjects(Manga::class.java)
                                .withQuery(Query.builder()
                                        .table(MangaTable.TABLE)
                                        .where("${MangaTable.COL_SOURCE} = $NHENTAI_SOURCE_ID")
                                        .build())
                                .prepare()
                                .executeAsBlocking()

                        nhentaiManga.forEach {
                            it.url = getUrlWithoutDomain(it.url)
                        }

                        db.db.put()
                                .objects(nhentaiManga)
                                // Extremely slow without the resolver :/
                                .withPutResolver(MangaUrlPutResolver())
                                .prepare()
                                .executeAsBlocking()
                    }
                }

                // Backup database in next release
                if (oldVersion < 2) {
                    backupDatabase(context, oldVersion)
                }

                if (oldVersion < 8405) {
                    db.inTransaction {
                        // Migrate HBrowse source IDs
                        db.lowLevel().executeSQL(RawQuery.builder()
                                .query("""
                            UPDATE ${MangaTable.TABLE}
                                SET ${MangaTable.COL_SOURCE} = $HBROWSE_SOURCE_ID
                                WHERE ${MangaTable.COL_SOURCE} = 1401584337232758222
                        """.trimIndent())
                                .affectsTables(MangaTable.TABLE)
                                .build())
                    }

                    // Cancel old scheduler jobs with old ids
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        context.jobScheduler.cancelAll()
                    }
                }

                // TODO BE CAREFUL TO NOT FUCK UP MergedSources IF CHANGING URLs

                preferences.eh_lastVersionCode().set(BuildConfig.VERSION_CODE)

                return true
            }
        } catch(e: Exception) {
            logger.e( "Failed to migrate app from $oldVersion -> ${BuildConfig.VERSION_CODE}!", e)
        }
        return false
    }

    fun migrateBackupEntry(backupEntry: BackupEntry): Observable<BackupEntry> {
        val (manga, chapters, categories, history, tracks) = backupEntry

        // Migrate HentaiCafe source IDs
        if(manga.source == 6908L) {
            manga.source = HENTAI_CAFE_SOURCE_ID
        }

        // Migrate nhentai URLs
        if(manga.source == NHENTAI_SOURCE_ID) {
            manga.url = getUrlWithoutDomain(manga.url)
        }

        // Allow importing of nhentai extension backups
        if(manga.source in BlacklistedSources.NHENTAI_EXT_SOURCES) {
            manga.source = NHENTAI_SOURCE_ID
        }

        // Allow importing of English PervEden extension backups
        if(manga.source in BlacklistedSources.PERVEDEN_EN_EXT_SOURCES) {
            manga.source = PERV_EDEN_EN_SOURCE_ID
        }

        // Allow importing of Italian PervEden extension backups
        if(manga.source in BlacklistedSources.PERVEDEN_IT_EXT_SOURCES) {
            manga.source = PERV_EDEN_IT_SOURCE_ID
        }

        // Allow importing of EHentai extension backups
        if(manga.source in BlacklistedSources.EHENTAI_EXT_SOURCES) {
            manga.source = EH_SOURCE_ID
        }

        return Observable.just(backupEntry)
    }

    private fun backupDatabase(context: Context, oldMigrationVersion: Int) {
        val backupLocation = File(File(context.filesDir, "exh_db_bck"), "$oldMigrationVersion.bck.db")
        if(backupLocation.exists()) return // Do not backup same version twice

        val dbLocation = context.getDatabasePath(db.lowLevel().sqliteOpenHelper().databaseName)
        try {
            dbLocation.copyTo(backupLocation, overwrite = true)
        } catch(t: Throwable) {
            XLog.w("Failed to backup database!")
        }
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null)
                out += "?" + uri.query
            if (uri.fragment != null)
                out += "#" + uri.fragment
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