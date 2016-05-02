package eu.kanade.tachiyomi.data.database.queries

import eu.kanade.tachiyomi.data.database.tables.CategoryTable as Category
import eu.kanade.tachiyomi.data.database.tables.ChapterTable as Chapter
import eu.kanade.tachiyomi.data.database.tables.MangaCategoryTable as MangaCategory
import eu.kanade.tachiyomi.data.database.tables.MangaTable as Manga

/**
 * Query to get the manga from the library, with their categories and unread count.
 */
val libraryQuery =
    "SELECT M.*, COALESCE(MC.${MangaCategory.COLUMN_CATEGORY_ID}, 0) AS ${Manga.COLUMN_CATEGORY} " +
    "FROM (" +
        "SELECT ${Manga.TABLE}.*, COALESCE(C.unread, 0) AS ${Manga.COLUMN_UNREAD} " +
        "FROM ${Manga.TABLE} " +
        "LEFT JOIN (" +
            "SELECT ${Chapter.COLUMN_MANGA_ID}, COUNT(*) AS unread " +
            "FROM ${Chapter.TABLE} " +
            "WHERE ${Chapter.COLUMN_READ} = 0 " +
            "GROUP BY ${Chapter.COLUMN_MANGA_ID}" +
        ") AS C " +
        "ON ${Manga.COLUMN_ID} = C.${Chapter.COLUMN_MANGA_ID} " +
        "WHERE ${Manga.COLUMN_FAVORITE} = 1 " +
        "GROUP BY ${Manga.COLUMN_ID} " +
        "ORDER BY ${Manga.COLUMN_TITLE}" +
    ") AS M " +
    "LEFT JOIN (" +
        "SELECT * FROM ${MangaCategory.TABLE}) AS MC " +
        "ON MC.${MangaCategory.COLUMN_MANGA_ID} = M.${Manga.COLUMN_ID}"

/**
 * Query to get the recent chapters of manga from the library up to a date.
 */
fun getRecentsQuery() =
    "SELECT ${Manga.TABLE}.${Manga.COLUMN_URL} as mangaUrl, * FROM ${Manga.TABLE} JOIN ${Chapter.TABLE} " +
    "ON ${Manga.TABLE}.${Manga.COLUMN_ID} = ${Chapter.TABLE}.${Chapter.COLUMN_MANGA_ID} " +
    "WHERE ${Manga.COLUMN_FAVORITE} = 1 AND ${Chapter.COLUMN_DATE_UPLOAD} > ? " +
    "ORDER BY ${Chapter.COLUMN_DATE_UPLOAD} DESC"


/**
 * Query to get the categories for a manga.
 */
fun getCategoriesForMangaQuery() =
    "SELECT ${Category.TABLE}.* FROM ${Category.TABLE} " +
    "JOIN ${MangaCategory.TABLE} ON ${Category.TABLE}.${Category.COLUMN_ID} = " +
    "${MangaCategory.TABLE}.${MangaCategory.COLUMN_CATEGORY_ID} " +
    "WHERE ${MangaCategory.COLUMN_MANGA_ID} = ?"