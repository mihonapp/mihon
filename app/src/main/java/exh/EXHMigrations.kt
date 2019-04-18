package exh

import com.elvishew.xlog.XLog
import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.backup.models.DHistory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.resolvers.MangaUrlPutResolver
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import exh.source.BlacklistedSources
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException

object EXHMigrations {
    private val db: DatabaseHelper by injectLazy()

    private val logger = XLog.tag("EXHMigrations")

    private const val CURRENT_MIGRATION_VERSION = 1

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
            if (oldVersion < CURRENT_MIGRATION_VERSION) {
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

                preferences.eh_lastVersionCode().set(CURRENT_MIGRATION_VERSION)

                return true
            }
        } catch(e: Exception) {
            logger.e( "Failed to migrate app from $oldVersion -> $CURRENT_MIGRATION_VERSION!", e)
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