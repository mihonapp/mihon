package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite
import eu.kanade.tachiyomi.data.database.mappers.ChapterTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaCategoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaTypeMapping
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.queries.MangaQueries

/**
 * This class provides operations to manage the database through its interfaces.
 */
class DatabaseHelper(
    openHelper: SupportSQLiteOpenHelper,
) :
    MangaQueries {

    override val db = DefaultStorIOSQLite.builder()
        .sqliteOpenHelper(openHelper)
        .addTypeMapping(Manga::class.java, MangaTypeMapping())
        .addTypeMapping(Chapter::class.java, ChapterTypeMapping())
        .addTypeMapping(MangaCategory::class.java, MangaCategoryTypeMapping())
        .build()
}
