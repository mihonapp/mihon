package exh

import com.pushtorefresh.storio.sqlite.queries.Query
import com.pushtorefresh.storio.sqlite.queries.RawQuery
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.resolvers.MangaUrlPutResolver
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException

object EXHMigrations {
    private val db: DatabaseHelper by injectLazy()

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
        if (oldVersion < CURRENT_MIGRATION_VERSION) {
            preferences.eh_lastVersionCode().set(CURRENT_MIGRATION_VERSION)

            if(oldVersion < 1) {
                db.inTransaction {
                    // Migrate HentaiCafe source IDs
                    db.lowLevel().executeSQL(RawQuery.builder()
                            .query("""
                            UPDATE ${MangaTable.TABLE}
                                SET ${MangaTable.COL_SOURCE} = 260868874183818481
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

            return true
        }
        return false
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