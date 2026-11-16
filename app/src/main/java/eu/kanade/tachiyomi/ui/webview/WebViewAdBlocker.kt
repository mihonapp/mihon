package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.util.AtomicFile
import androidx.core.util.writeBytes
import app.komikita.core.nativecore.NativeAdBlockEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
class WebViewAdBlocker(
    private val context: Context,
    preferenceStore: PreferenceStore,
    json: Json,
    @AppCoroutineScope scope: CoroutineScope,
) {
    private val preference = preferenceStore.getObjectFromString(
        key = "webview_ad_block_settings",
        defaultValue = Settings(),
        serializer = { json.encodeToString(it) },
        deserializer = { json.decodeFromString<Settings>(it) },
    )
    private val mutex = Mutex()
    private val client by lazy { OkHttpClient.Builder().callTimeout(30.seconds).build() }
    private val mutableSettings = MutableStateFlow<Settings?>(null)
    val settings = mutableSettings.asStateFlow()

    /** Null when the native engine is handling requests. */
    @Volatile
    private var fallbackRules: WebViewAdBlockRules? = WebViewAdBlockRules.Empty

    val supportsBraveFilters: Boolean
        get() = NativeAdBlockEngine.isAvailable

    init {
        scope.launch {
            preference.changes().collect {
                mutex.withLock {
                    val saved = preference.get()
                    if (saved != mutableSettings.value) publish(saved)
                }
            }
        }
    }

    fun shouldBlock(url: String, host: String?, sourceUrl: String, resourceType: String, method: String): Boolean {
        return fallbackRules?.blocks(host, isMainFrame = false)
            ?: NativeAdBlockEngine.shouldBlock(url, sourceUrl, resourceType, method)
    }

    suspend fun setEnabled(enabled: Boolean) = update { save(it.copy(enabled = enabled)) }

    suspend fun setBuiltInEnabled(enabled: Boolean) = update { save(it.copy(builtInEnabled = enabled)) }

    suspend fun setCustomRulesEnabled(enabled: Boolean) = update { save(it.copy(customRulesEnabled = enabled)) }

    suspend fun saveCustomRules(text: String): FilterError? = update { current ->
        if (text.length > MAX_CUSTOM_RULE_CHARS) return@update FilterError.TooLarge
        if (!validateRules(text, strict = true)) {
            return@update FilterError.UnsupportedRules
        }
        save(current.copy(customRules = text))
        null
    }

    suspend fun setListEnabled(url: String, enabled: Boolean) = update { current ->
        save(current.copy(lists = current.lists.map { if (it.url == url) it.copy(enabled = enabled) else it }))
    }

    suspend fun removeList(url: String) = update { current ->
        save(current.copy(lists = current.lists.filterNot { it.url == url }))
        listFile(url).delete()
    }

    suspend fun downloadList(input: String, add: Boolean): FilterError? = update { current ->
        val url = input.trim().toHttpUrlOrNull()
            ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
            ?.newBuilder()
            ?.fragment(null)
            ?.build()
            ?: return@update FilterError.InvalidUrl
        val key = url.toString()
        val exists = current.lists.any { it.url == key }
        if (add && exists) return@update FilterError.DuplicateUrl
        if (!add && !exists) return@update null

        try {
            val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
            val bytes = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@update FilterError.DownloadFailed
                val body = response.body.source()
                if (body.request(MAX_LIST_BYTES + 1)) return@update FilterError.TooLarge
                body.readByteArray()
            }
            if (!validateRules(bytes.decodeToString(), strict = false)) return@update FilterError.UnsupportedRules

            currentCoroutineContext().ensureActive()
            listFile(key).writeBytes(bytes)
            save(if (add) current.copy(lists = current.lists + FilterList(key)) else current)
            null
        } catch (_: IOException) {
            FilterError.DownloadFailed
        }
    }

    private suspend fun <T> update(action: suspend (Settings) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { action(preference.get()) }
    }

    private fun save(settings: Settings) {
        preference.set(settings)
        publish(settings)
    }

    private fun publish(settings: Settings) {
        fallbackRules = if (settings.enabled) {
            loadRules(settings)
        } else {
            if (fallbackRules == null) NativeAdBlockEngine.replaceRules("")
            WebViewAdBlockRules.Empty
        }
        mutableSettings.value = settings
    }

    private fun validateRules(text: String, strict: Boolean): Boolean {
        if (text.trimStart('\uFEFF').trimStart().startsWith('<')) return false
        if (supportsBraveFilters) {
            val rules = buildString { appendBraveRules(text.lineSequence()) }
            NativeAdBlockEngine.validateRules(rules, strict)?.let { return it }
        }
        val builder = WebViewAdBlockRules.Builder()
        val unsupported = builder.addRules(text.lineSequence())
        return if (strict) unsupported == 0 else builder.hasRules
    }

    private fun loadRules(settings: Settings): WebViewAdBlockRules? {
        if (supportsBraveFilters) {
            val rules = buildString { readFilterLists(settings) { appendBraveRules(it) } }
            if (NativeAdBlockEngine.replaceRules(rules)) return null
            logcat(LogPriority.WARN) { "Could not load native WebView filters" }
        }
        return WebViewAdBlockRules.Builder().apply {
            readFilterLists(settings) { addRules(it) }
        }.build()
    }

    private fun readFilterLists(settings: Settings, addRules: (Sequence<String>) -> Unit) {
        if (settings.builtInEnabled) addRules(BUILT_IN_DOMAINS.asSequence())
        if (settings.customRulesEnabled) addRules(settings.customRules.lineSequence())
        for (list in settings.lists) {
            if (!list.enabled) continue
            try {
                listFile(list.url).openRead().bufferedReader().useLines(addRules)
            } catch (_: FileNotFoundException) {
                // Restored settings can refer to lists that have yet to be downloaded on this device.
            } catch (e: IOException) {
                logcat(LogPriority.WARN, e) { "Failed to read a WebView ad filter list" }
            }
        }
    }

    private fun listFile(url: String): AtomicFile {
        return AtomicFile(File(context.filesDir, "webview-ad-block/${url.encodeUtf8().sha256().hex()}.txt"))
    }

    @Serializable
    data class Settings(
        val enabled: Boolean = true,
        val builtInEnabled: Boolean = true,
        val customRulesEnabled: Boolean = true,
        val customRules: String = "",
        val lists: List<FilterList> = emptyList(),
    )

    @Serializable
    data class FilterList(
        val url: String,
        val enabled: Boolean = true,
    )

    enum class FilterError {
        InvalidUrl,
        DuplicateUrl,
        UnsupportedRules,
        TooLarge,
        DownloadFailed,
    }

    private companion object {
        const val MAX_LIST_BYTES = 8L * 1024 * 1024
        const val MAX_CUSTOM_RULE_CHARS = 64 * 1024

        val BUILT_IN_DOMAINS = listOf(
            "a-ads.com",
            "adform.net",
            "adnxs.com",
            "adsrvr.org",
            "adsterra.com",
            "advertising.com",
            "bidswitch.net",
            "casalemedia.com",
            "criteo.com",
            "criteo.net",
            "doubleclick.net",
            "exoclick.com",
            "googleadservices.com",
            "googlesyndication.com",
            "infolinks.com",
            "lijit.com",
            "media.net",
            "mgid.com",
            "onclicka.com",
            "openx.net",
            "outbrain.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "pubmatic.com",
            "realsrv.com",
            "revcontent.com",
            "rubiconproject.com",
            "smartadserver.com",
            "taboola.com",
            "teads.tv",
            "trafficjunky.net",
        )
    }
}
