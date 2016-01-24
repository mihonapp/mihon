package eu.kanade.tachiyomi.data.backup.serializer

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaSync

class IdExclusion : ExclusionStrategy {

    private val categoryExclusions = listOf("id")
    private val mangaExclusions = listOf("id")
    private val chapterExclusions = listOf("id", "manga_id")
    private val syncExclusions = listOf("id", "manga_id", "update")

    override fun shouldSkipField(f: FieldAttributes) = when (f.declaringClass) {
        Manga::class.java -> mangaExclusions.contains(f.name)
        Chapter::class.java -> chapterExclusions.contains(f.name)
        MangaSync::class.java -> syncExclusions.contains(f.name)
        Category::class.java -> categoryExclusions.contains(f.name)
        else -> false
    }

    override fun shouldSkipClass(clazz: Class<*>) = false

}
