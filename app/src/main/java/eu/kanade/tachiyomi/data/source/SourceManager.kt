package eu.kanade.tachiyomi.data.source

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Context
import android.os.Environment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.source.online.OnlineSource
import eu.kanade.tachiyomi.data.source.online.YamlOnlineSource
import eu.kanade.tachiyomi.data.source.online.all.EHentai
import eu.kanade.tachiyomi.data.source.online.all.EHentaiMetadata
import eu.kanade.tachiyomi.data.source.online.english.*
import eu.kanade.tachiyomi.data.source.online.german.WieManga
import eu.kanade.tachiyomi.data.source.online.russian.Mangachan
import eu.kanade.tachiyomi.data.source.online.russian.Mintmanga
import eu.kanade.tachiyomi.data.source.online.russian.Readmanga
import eu.kanade.tachiyomi.util.hasPermission
import org.yaml.snakeyaml.Yaml
import rx.functions.Action1
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File

open class SourceManager(private val context: Context) {

    private val prefs: PreferencesHelper by injectLazy()

    private var sourcesMap = createSources()

    open fun get(sourceKey: Int): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance(OnlineSource::class.java)

    private fun createOnlineSourceList(): List<Source> = listOf(
            Batoto(101),
            Mangahere(102),
            Mangafox(103),
            Kissmanga(104),
            Readmanga(105),
            Mintmanga(106),
            Mangachan(107),
            Readmangatoday(108),
            Mangasee(109),
            WieManga(110)
    )

    private fun createEHSources(): List<Source> {
        val exSrcs = mutableListOf(
                EHentai(1, false, context),
                EHentaiMetadata(3, false, context)
        )
        if(prefs.enableExhentai().getOrDefault()) {
            exSrcs += EHentai(2, true, context)
            exSrcs += EHentaiMetadata(4, true, context)
        }
        return exSrcs
    }

    init {
        //Rebuild EH when settings change
        val action: Action1<Any> = Action1 { sourcesMap = createSources() }

        prefs.enableExhentai().asObservable().subscribe(action)
        prefs.imageQuality().asObservable().subscribe (action)
        prefs.useHentaiAtHome().asObservable().subscribe(action)
        prefs.useJapaneseTitle().asObservable().subscribe {
            action.call(null)
        }
        prefs.ehSearchSize().asObservable().subscribe (action)
        prefs.thumbnailRows().asObservable().subscribe(action)
    }

    private fun createSources(): Map<Int, Source> = hashMapOf<Int, Source>().apply {
        createEHSources().forEach { put(it.id, it) }
        createOnlineSourceList().forEach { put(it.id, it) }

        val parsersDir = File(Environment.getExternalStorageDirectory().absolutePath +
                File.separator + context.getString(R.string.app_name), "parsers")

        if (parsersDir.exists() && context.hasPermission(READ_EXTERNAL_STORAGE)) {
            val yaml = Yaml()
            for (file in parsersDir.listFiles().filter { it.extension == "yml" }) {
                try {
                    val map = file.inputStream().use { yaml.loadAs(it, Map::class.java) }
                    YamlOnlineSource(map).let { put(it.id, it) }
                } catch (e: Exception) {
                    Timber.e("Error loading source from file. Bad format?")
                }
            }
        }
    }

}
