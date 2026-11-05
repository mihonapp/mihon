package eu.kanade.tachiyomi.data.translation.overlay

import android.content.Context
import android.graphics.Typeface
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages loading and caching of specialized fonts (e.g. Arabic manga and custom fonts)
 * for the translation overlay renderer.
 */
object PageOverlayFontManager {

    private var contextRef: WeakReference<Context>? = null
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    @Volatile
    var customFontPath: String = ""

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    /**
     * Resolves the appropriate [Typeface] for the given [font].
     * Returns null if standard system fonts should be used or if on a JVM test without Context.
     */
    fun getTypeface(font: RenderStyle.Font): Typeface? {
        val context = contextRef?.get() ?: return null

        return when (font) {
            RenderStyle.Font.ARABIC_MANGA -> typefaceCache.getOrPut("arabic_manga") {
                loadAssetTypeface(context, "fonts/manga_arabic.ttf")
                    ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            RenderStyle.Font.ARABIC_RUQAA -> typefaceCache.getOrPut("arabic_ruqaa") {
                loadAssetTypeface(context, "fonts/manga_ruqaa.ttf")
                    ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            RenderStyle.Font.CUSTOM -> {
                val path = customFontPath.trim()
                if (path.isNotEmpty()) {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        typefaceCache.getOrPut("custom_${file.lastModified()}") {
                            try {
                                Typeface.createFromFile(file)
                            } catch (e: Exception) {
                                logcat(LogPriority.WARN, e) { "Could not load custom font from $path" }
                                Typeface.DEFAULT_BOLD
                            }
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun loadAssetTypeface(context: Context, assetPath: String): Typeface? {
        return try {
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not load font from asset $assetPath" }
            null
        }
    }
}
