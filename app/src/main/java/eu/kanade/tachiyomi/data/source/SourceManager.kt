package eu.kanade.tachiyomi.data.source

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.Context
import android.os.Environment
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.source.online.OnlineSource
import eu.kanade.tachiyomi.data.source.online.YamlOnlineSource
import eu.kanade.tachiyomi.data.source.online.english.*
import eu.kanade.tachiyomi.util.hasPermission
import exh.DialogLogin
import org.yaml.snakeyaml.Yaml
import timber.log.Timber
import java.io.File

open class SourceManager(private val context: Context) {

    val EHENTAI = 1
    val EXHENTAI = 2

    val LAST_SOURCE by lazy {
        if (DialogLogin.isLoggedIn(context, false))
            2
        else
            1
    }

    val sourcesMap = createSources()

    open fun get(sourceKey: Int): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance(OnlineSource::class.java)

    private fun createSource(id: Int): Source? = when (id) {
        EHENTAI -> EHentai(context, id, false)
        EXHENTAI -> EHentai(context, id, true)
        else -> null
    }

    private fun createSources(): Map<Int, Source> = hashMapOf<Int, Source>().apply {
        for (i in 1..LAST_SOURCE) {
            createSource(i)?.let { put(i, it) }
        }

        val parsersDir = File(Environment.getExternalStorageDirectory().absolutePath +
                File.separator + context.getString(R.string.app_name), "parsers")

        if (parsersDir.exists() && context.hasPermission(READ_EXTERNAL_STORAGE)) {
            val yaml = Yaml()
            for (file in parsersDir.listFiles().filter { it.extension == "yml" }) {
                try {
                    val map = file.inputStream().use { yaml.loadAs(it, Map::class.java) }
                    YamlOnlineSource(context, map).let { put(it.id, it) }
                } catch (e: Exception) {
                    Timber.e("Error loading source from file. Bad format?")
                }
            }
        }
    }

}
