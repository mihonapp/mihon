package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.pushtorefresh.storio.sqlite.impl.DefaultStorIOSQLite
import eu.kanade.tachiyomi.data.database.mappers.CategoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.ChapterTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.HistoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaCategoryTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.MangaTypeMapping
import eu.kanade.tachiyomi.data.database.mappers.TrackTypeMapping
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.queries.CategoryQueries
import eu.kanade.tachiyomi.data.database.queries.ChapterQueries
import eu.kanade.tachiyomi.data.database.queries.HistoryQueries
import eu.kanade.tachiyomi.data.database.queries.MangaCategoryQueries
import eu.kanade.tachiyomi.data.database.queries.MangaQueries
import eu.kanade.tachiyomi.data.database.queries.TrackQueries

/**
 * This class provides operations to manage the database through its interfaces.
 */
class DatabaseHelper(
    openHelper: SupportSQLiteOpenHelper,
) :
    MangaQueries, ChapterQueries, TrackQueries, CategoryQueries, MangaCategoryQueries, HistoryQueries {

    override val db = DefaultStorIOSQLite.builder()
        .sqliteOpenHelper(openHelper)
        .addTypeMapping(Manga::class.java, MangaTypeMapping())
        .addTypeMapping(Chapter::class.java, ChapterTypeMapping())
        .addTypeMapping(Track::class.java, TrackTypeMapping())
        .addTypeMapping(Category::class.java, CategoryTypeMapping())
        .addTypeMapping(MangaCategory::class.java, MangaCategoryTypeMapping())
        .addTypeMapping(History::class.java, HistoryTypeMapping())
        .build()

    inline fun inTransaction(block: () -> Unit) = db.inTransaction(block)
}
