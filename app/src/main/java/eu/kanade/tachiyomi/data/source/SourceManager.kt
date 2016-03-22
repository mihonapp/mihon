package eu.kanade.tachiyomi.data.source

import android.content.Context
import eu.kanade.tachiyomi.data.source.base.Source
import eu.kanade.tachiyomi.data.source.online.english.Batoto
import eu.kanade.tachiyomi.data.source.online.english.Kissmanga
import eu.kanade.tachiyomi.data.source.online.english.Mangafox
import eu.kanade.tachiyomi.data.source.online.english.Mangahere
import eu.kanade.tachiyomi.data.source.online.russian.Mangachan;
import eu.kanade.tachiyomi.data.source.online.russian.Mintmanga;
import eu.kanade.tachiyomi.data.source.online.russian.Readmanga;
import eu.kanade.tachiyomi.data.source.online.english.ReadMangaToday
import java.util.*

open class SourceManager(private val context: Context) {

    val sourcesMap: HashMap<Int, Source>

    val BATOTO = 1
    val MANGAHERE = 2
    val MANGAFOX = 3
    val KISSMANGA = 4
    val READMANGA = 5
    val MINTMANGA = 6
    val MANGACHAN = 7
    val READMANGATODAY = 8

    val LAST_SOURCE = 8

    init {
        sourcesMap = createSourcesMap()
    }

    open fun get(sourceKey: Int): Source? {
        return sourcesMap[sourceKey]
    }

    private fun createSource(sourceKey: Int): Source? = when (sourceKey) {
        BATOTO -> Batoto(context)
        MANGAHERE -> Mangahere(context)
        MANGAFOX -> Mangafox(context)
        KISSMANGA -> Kissmanga(context)
        READMANGA -> Readmanga(context)
        MINTMANGA -> Mintmanga(context)
        MANGACHAN -> Mangachan(context)
        READMANGATODAY -> ReadMangaToday(context)
        else -> null
    }

    private fun createSourcesMap(): HashMap<Int, Source> {
        val map = HashMap<Int, Source>()
        for (i in 1..LAST_SOURCE) {
            val source = createSource(i)
            if (source != null) {
                source.id = i
                map.put(i, source)
            }
        }
        return map
    }

    fun getSources(): List<Source> = ArrayList(sourcesMap.values)

}
