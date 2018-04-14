package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.Hitomi
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.PervEden
import eu.kanade.tachiyomi.source.online.english.*
import eu.kanade.tachiyomi.source.online.german.WieManga
import eu.kanade.tachiyomi.source.online.russian.Mangachan
import eu.kanade.tachiyomi.source.online.russian.Mintmanga
import eu.kanade.tachiyomi.source.online.russian.Readmanga
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import exh.PERV_EDEN_EN_SOURCE_ID
import exh.PERV_EDEN_IT_SOURCE_ID
import exh.metadata.models.PervEdenLang
import rx.Observable
import uy.kohesive.injekt.injectLazy

open class SourceManager(private val context: Context) {

    private val prefs: PreferencesHelper by injectLazy()

    private val sourcesMap = mutableMapOf<Long, Source>()

    init {
        createInternalSources().forEach { registerSource(it) }

        //Recreate sources when they change
        val prefEntries = arrayOf(
                prefs.enableExhentai(),
                prefs.imageQuality(),
                prefs.useHentaiAtHome(),
                prefs.useJapaneseTitle(),
                prefs.ehSearchSize(),
                prefs.thumbnailRows()
        ).map { it.asObservable() }

        Observable.merge(prefEntries).skip(prefEntries.size - 1).subscribe {
            createEHSources().forEach { registerSource(it) }
        }
    }

    open fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    internal fun registerSource(source: Source, overwrite: Boolean = false) {
        if (overwrite || !sourcesMap.containsKey(source.id)) {
            sourcesMap.put(source.id, source)
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
    }

    private fun createInternalSources(): List<Source> = listOf(
            LocalSource(context),
            Batoto(),
            Mangahere(),
            Mangafox(),
            Kissmanga(),
            Readmanga(),
            Mintmanga(),
            Mangachan(),
            Readmangatoday(),
            Mangasee(),
            WieManga()
    )

    private fun createEHSources(): List<Source> {
        val exSrcs = mutableListOf<HttpSource>(
                EHentai(EH_SOURCE_ID, false, context)
        )
        if(prefs.enableExhentai().getOrDefault()) {
            exSrcs += EHentai(EXH_SOURCE_ID, true, context)
        }
        exSrcs += PervEden(PERV_EDEN_EN_SOURCE_ID, PervEdenLang.en)
        exSrcs += PervEden(PERV_EDEN_IT_SOURCE_ID, PervEdenLang.it)
        exSrcs += NHentai(context)
        exSrcs += HentaiCafe()
        exSrcs += Tsumino(context)
        exSrcs += Hitomi(context)
        return exSrcs
    }
}
