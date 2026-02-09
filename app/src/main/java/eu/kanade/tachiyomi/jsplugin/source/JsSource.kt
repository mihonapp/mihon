package eu.kanade.tachiyomi.jsplugin.source

import android.content.Context
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.jsplugin.model.InstalledJsPlugin
import eu.kanade.tachiyomi.jsplugin.model.JsPlugin
import eu.kanade.tachiyomi.jsplugin.runtime.PluginRuntime
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A CatalogueSource implementation backed by a JavaScript plugin.
 * Executes LNReader-compatible plugins through JsPluginRuntime.
 */
class JsSource(
    private val installedPlugin: InstalledJsPlugin,
) : CatalogueSource, ConfigurableSource, NovelSource {

    private val plugin: JsPlugin = installedPlugin.plugin
    private val jsCode: String = installedPlugin.code
    private val context: Context = Injekt.get()
    private val networkHelper: NetworkHelper = Injekt.get()

    private val json = Json { ignoreUnknownKeys = true }

    // Single-thread executor for JS execution to avoid JNI caching issues
    // QuickJS caches the JNI environment on the thread that creates it,
    // so all operations must happen on the same thread
    private val jsExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "JsSource-$pluginId").apply { isDaemon = true }
    }
    private val jsDispatcher = jsExecutor.asCoroutineDispatcher()

    // Cached runtime instance to avoid recreating for every method call
    @Volatile private var cachedInstance: eu.kanade.tachiyomi.jsplugin.runtime.PluginInstance? = null
    private val instanceLock = Any()
    private var lastUsed = System.currentTimeMillis()

    // Cache manga details and chapters to prevent re-fetching
    private val detailsCache = mutableMapOf<String, Pair<SManga, Long>>()
    private val chaptersCache = mutableMapOf<String, Pair<List<SChapter>, Long>>()
    private val cacheTimeout = 300_000L // 5 minutes

    companion object {
        private const val INSTANCE_TIMEOUT_MS = 60_000L // 1 minute timeout
    }

    private val pluginId: String = plugin.id

    override val id: Long = plugin.sourceId()
    override val name: String = plugin.name
    override val lang: String = plugin.langCode()
    override val supportsLatest: Boolean = true

    // Novel source marker
    override val isNovelSource: Boolean = true

    // Visible name of the source with language and JS marker
    override fun toString(): String = "$name (${lang.uppercase()}) (JS)"

    val baseUrl: String = plugin.site?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "https://example.com"
    val iconUrl: String = plugin.iconUrl
    val version: String = plugin.version

    private fun codeLooksTruncated(code: String): Boolean {
        if (code.isBlank()) return true
        if (!code.contains("exports.default")) return true
        // A very lightweight sanity check: most of these plugins are big, minified blobs.
        // If braces are unbalanced it's a strong indicator of truncation.
        val open = code.count { it == '{' }
        val close = code.count { it == '}' }
        return open != close
    }

    private suspend fun maybeHealCode(original: String): String {
        val url = plugin.url
        if (url.isBlank()) return original
        if (!codeLooksTruncated(original)) return original

        return try {
            logcat(LogPriority.WARN) { "JsSource[${plugin.id}]: plugin code looks truncated (len=${original.length}); re-downloading from $url" }
            val response = networkHelper.client.newCall(GET(url)).execute()
            if (!response.isSuccessful) {
                logcat(LogPriority.WARN) { "JsSource[${plugin.id}]: re-download failed HTTP ${response.code}" }
                return original
            }
            val fresh = response.body?.string().orEmpty()
            if (fresh.isBlank()) return original
            logcat(LogPriority.INFO) { "JsSource[${plugin.id}]: re-downloaded plugin code (len=${fresh.length})" }
            fresh
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "JsSource[${plugin.id}]: re-download failed" }
            original
        }
    }

    /**
     * Get or create a cached plugin instance to avoid expensive re-initialization.
     * Must be called from jsDispatcher to ensure proper JNI environment.
     */
    private suspend fun getOrCreateInstance(): eu.kanade.tachiyomi.jsplugin.runtime.PluginInstance = withContext(jsDispatcher) {
        synchronized(instanceLock) {
            val now = System.currentTimeMillis()
            val existing = cachedInstance

            // Return existing instance if still valid
            if (existing != null && (now - lastUsed) < INSTANCE_TIMEOUT_MS) {
                lastUsed = now
                return@withContext existing
            }

            // Close old instance if timed out
            existing?.close()
            cachedInstance = null
        }

        // Create new instance outside lock to avoid blocking
        val codeToUse = maybeHealCode(jsCode)
        val runtime = PluginRuntime(pluginId, context, jsDispatcher)
        val newInstance = try {
            runtime.executePlugin(codeToUse)
        } catch (e: Exception) {
            // If plugin execution fails, log and rethrow
            logcat(LogPriority.ERROR, e) { "JsSource[$pluginId]: Failed to execute plugin" }
            throw e
        }

        synchronized(instanceLock) {
            // Double-check: another thread might have created one
            val existing = cachedInstance
            if (existing != null) {
                newInstance.close()
                lastUsed = System.currentTimeMillis()
                return@withContext existing
            }

            cachedInstance = newInstance
            lastUsed = System.currentTimeMillis()
            newInstance
        }
    }

    /**
     * Invalidate the cached instance (call on errors).
     */
    private fun invalidateInstance() {
        synchronized(instanceLock) {
            cachedInstance?.close()
            cachedInstance = null
        }
    }

    /**
     * Force cleanup of resources. Call when navigating away.
     */
    fun cleanup() {
        invalidateInstance()
        jsExecutor.shutdown()
    }

    /**
     * Execute a plugin method and return JSON result.
     * All JS execution happens on jsDispatcher to ensure JNI environment is consistent.
     *
     * Note: some plugin methods are async and return a Promise (via TS __awaiter).
     * QuickJS-KT doesn't always properly await Promises returned from evaluate().
     * We use a global variable approach to store the result after Promise resolution.
     */
    private suspend fun executePluginMethod(methodCall: String): String = withContext(jsDispatcher) {
        val instance = try {
            getOrCreateInstance()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "JsSource[$pluginId]: Failed to create plugin instance" }
            throw e
        }

        val token = "mihon_${System.nanoTime()}"
        try {
            logcat(LogPriority.DEBUG) { "JsSource[$pluginId]: Executing: $methodCall" }

            // Store result in global variable, handle Promise resolution in JS
            instance.execute(
                """
                (function() {
                    globalThis.__mihon_result_$token = null;
                    globalThis.__mihon_error_$token = null;
                    globalThis.__mihon_done_$token = false;

                    try {
                        var maybePromise = ($methodCall);
                        Promise.resolve(maybePromise).then(function(result) {
                            try {
                                globalThis.__mihon_result_$token = JSON.stringify(result);
                            } catch(e) {
                                globalThis.__mihon_result_$token = 'null';
                            }
                            globalThis.__mihon_done_$token = true;
                        }).catch(function(e) {
                            globalThis.__mihon_error_$token = (e && e.stack) ? (String(e) + '\n' + e.stack) : String(e);
                            globalThis.__mihon_done_$token = true;
                        });
                    } catch(e) {
                        globalThis.__mihon_error_$token = (e && e.stack) ? (String(e) + '\n' + e.stack) : String(e);
                        globalThis.__mihon_done_$token = true;
                    }
                })();
                """.trimIndent()
            )

            // Poll for completion with proper async waiting
            // QuickJS asyncFunction needs actual time to execute Kotlin coroutines
            var attempts = 0
            val maxAttempts = 600 // 30 seconds max (600 * 50ms)
            while (attempts < maxAttempts) {
                val done = instance.execute("globalThis.__mihon_done_$token") as? Boolean ?: false
                if (done) break
                attempts++
                // Give async functions time to execute their Kotlin coroutines
                // This is necessary because asyncFunction runs actual network requests
                kotlinx.coroutines.delay(50)
                // Also process JS microtasks
                instance.execute("null")
            }

            if (attempts >= maxAttempts) {
                logcat(LogPriority.WARN) { "JsSource[$pluginId]: Execution timed out after ${maxAttempts * 50}ms" }
            }

            // Read results FIRST before cleanup
            val error = instance.execute("globalThis.__mihon_error_$token") as? String
            val jsonResult = instance.execute("globalThis.__mihon_result_$token") as? String

            // Cleanup global variables AFTER reading
            instance.execute("""
                delete globalThis.__mihon_result_$token;
                delete globalThis.__mihon_error_$token;
                delete globalThis.__mihon_done_$token;
                if (globalThis.__clearCheerioCache) {
                    globalThis.__clearCheerioCache();
                }
            """.trimIndent())

            // Check for errors - "null" string from error means no error, not "Plugin error: null"
            if (!error.isNullOrEmpty() && error != "null") {
                throw Exception("Plugin error: $error")
            }

            logcat(LogPriority.DEBUG) { "JsSource[$pluginId]: Result: ${jsonResult?.take(200)}" }
            jsonResult ?: "null"
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "JsSource[$pluginId]: Error executing: $methodCall" }
            // Invalidate on SyntaxError or critical errors
            val message = e.message.orEmpty()
            if (message.contains("SyntaxError", ignoreCase = true) ||
                message.contains("vm is not cached", ignoreCase = true)) {
                invalidateInstance()
            }
            throw e
        }
    }

    // CatalogueSource implementation

    override suspend fun getPopularManga(page: Int): MangasPage = withContext(Dispatchers.IO) {
        try {
            logcat(LogPriority.DEBUG) { "JsSource[${plugin.id}].getPopularManga: page=$page" }
            // Fetch page content and execute plugin
            val result = executePluginMethod("plugin.popularNovels($page, { showLatestNovels: false, filters: plugin.filters })")
            logcat(LogPriority.DEBUG) { "JsSource[${plugin.id}].getPopularManga: result ${result}" }
            parseMangasPage(result, page)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getPopularManga for ${plugin.name}" }
            MangasPage(emptyList(), false)
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
        try {
            logcat(LogPriority.DEBUG) { "JsSource[${plugin.id}].getLatestUpdates: page=$page" }
            val result = executePluginMethod("plugin.popularNovels($page, { showLatestNovels: true, filters: plugin.filters })")
            logcat(LogPriority.DEBUG) { "JsSource[${plugin.id}].getLatestUpdates: result${result}" }
            parseMangasPage(result, page)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getLatestUpdates for ${plugin.name}" }
            MangasPage(emptyList(), false)
        }
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withContext(Dispatchers.IO) {
        try {
            val escapedQuery = query.replace("'", "\\'").replace("\"", "\\\"")

            // Determine if non-default filters are present
            val hasActiveFilters = filters.isNotEmpty() && filters.any { filter ->
                when (filter) {
                    is JsSelectFilter -> filter.state != 0
                    is JsCheckboxGroup -> filter.selectedValues().isNotEmpty()
                    is JsTriStateGroup -> filter.includedValues().isNotEmpty() || filter.excludedValues().isNotEmpty()
                    is JsSwitchFilter -> filter.state
                    is JsTextFilter -> filter.state.isNotBlank()
                    is Filter.CheckBox -> filter.state
                    is Filter.Text -> filter.state.isNotBlank()
                    else -> false
                }
            }

            if (query.isNotBlank()) {
                // Use searchNovels for text search
                val result = executePluginMethod("plugin.searchNovels('$escapedQuery', $page)")
                parseMangasPage(result, page)
            } else if (hasActiveFilters) {
                // Use popularNovels with user-modified filters
                val filtersJs = convertFiltersToJs(filters)
                val result = executePluginMethod("plugin.popularNovels($page, { showLatestNovels: false, filters: $filtersJs })")
                parseMangasPage(result, page)
            } else {
                // Default to popular with plugin's original filters
                val result = executePluginMethod("plugin.popularNovels($page, { showLatestNovels: false, filters: plugin.filters })")
                parseMangasPage(result, page)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getSearchManga for ${plugin.name}" }
            MangasPage(emptyList(), false)
        }
    }

    /**
     * Convert Mihon FilterList back to JS filter object format for plugin.
     * Uses JsonObject builder to avoid kotlinx.serialization issues with Map<String, Any>.
     */
    private fun convertFiltersToJs(filters: FilterList): String {
        val filterEntries = mutableMapOf<String, JsonElement>()

        filters.forEach { filter ->
            when (filter) {
                is JsSelectFilter -> {
                    filterEntries[filter.key] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Picker"),
                            "value" to JsonPrimitive(filter.selectedValue()),
                        ),
                    )
                }
                is JsCheckboxGroup -> {
                    filterEntries[filter.key] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Checkbox"),
                            "value" to JsonArray(filter.selectedValues().map { JsonPrimitive(it) }),
                        ),
                    )
                }
                is JsTriStateGroup -> {
                    filterEntries[filter.key] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("XCheckbox"),
                            "value" to JsonObject(
                                mapOf(
                                    "include" to JsonArray(filter.includedValues().map { JsonPrimitive(it) }),
                                    "exclude" to JsonArray(filter.excludedValues().map { JsonPrimitive(it) }),
                                ),
                            ),
                        ),
                    )
                }
                is JsSwitchFilter -> {
                    filterEntries[filter.key] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Switch"),
                            "value" to JsonPrimitive(filter.state),
                        ),
                    )
                }
                is Filter.CheckBox -> {
                    filterEntries[filter.name] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Switch"),
                            "value" to JsonPrimitive(filter.state),
                        ),
                    )
                }
                is JsTextFilter -> {
                    filterEntries[filter.key] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Text"),
                            "value" to JsonPrimitive(filter.state),
                        ),
                    )
                }
                is Filter.Text -> {
                    filterEntries[filter.name] = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("Text"),
                            "value" to JsonPrimitive(filter.state),
                        ),
                    )
                }
                else -> {
                    // Ignore other filter types (headers, separators)
                }
            }
        }

        return JsonObject(filterEntries).toString()
    }

    override suspend fun getMangaDetails(manga: SManga): SManga = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = detailsCache[manga.url]
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.second) < cacheTimeout) {
                return@withContext cached.first
            }

            val path = manga.url.replace("'", "\\'").replace("\"", "\\\"")
            val result = executePluginMethod("plugin.parseNovel('$path')")
            val details = parseNovelDetails(result, manga)

            // Cache the result
            detailsCache[manga.url] = details to now
            details
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getMangaDetails for ${plugin.name}" }
            manga
        }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = chaptersCache[manga.url]
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.second) < cacheTimeout) {
                return@withContext cached.first
            }

            val path = manga.url.replace("'", "\\'").replace("\"", "\\\"")
            val result = executePluginMethod("plugin.parseNovel('$path')")
            val chapters = parseChapterList(result).toMutableList()

            // Support paged chapter sources (like novelight, novelfire)
            // If parseNovel returns totalPages > 1, fetch remaining pages via parsePage
            val totalPages = try {
                val obj = json.parseToJsonElement(result).jsonObject
                obj["totalPages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            } catch (_: Exception) { 1 }

         val needsPaging = (chapters.isEmpty() && totalPages >= 0) || totalPages > 0
            if (needsPaging) {
                val startPage = if (chapters.isEmpty()) 1 else 2
                logcat(LogPriority.DEBUG) { "JsSource[$pluginId]: Paged source detected, totalPages=$totalPages, startPage=$startPage, existingChapters=${chapters.size}" }
                for (page in startPage..totalPages) {
                    try {
                        val pageResult = executePluginMethod("plugin.parsePage('$path', '$page')")
                        val pageChapters = parsePageChapters(pageResult)
                        chapters.addAll(pageChapters)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "JsSource[$pluginId]: Error fetching page $page of $totalPages" }
                    }
                }
            }

            // LNReader plugins return newest-first. Reverse the entire combined list
            // so oldest chapters come first (index 0 = Ch1), then assign chapter_number
            // globally based on final position so numbering is consistent across pages.
            chapters.reverse()
            chapters.forEachIndexed { index, chapter ->
                // Only override chapter_number if the plugin didn't provide one
                // (default SChapter chapter_number is -1)
                if (chapter.chapter_number < 0 || chapters.size > 1) {
                    chapter.chapter_number = (index + 1).toFloat()
                }
            }

            // Cache the result
            chaptersCache[manga.url] = chapters to now
            chapters
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getChapterList for ${plugin.name}" }
            emptyList()
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = withContext(Dispatchers.IO) {
        try {
            // Validate chapter URL is not empty/blank to avoid fetching base URL
            if (chapter.url.isBlank()) {
                logcat(LogPriority.ERROR) { "[$id] getPageList: chapter.url is blank, cannot parse chapter" }
                return@withContext emptyList()
            }
            val path = chapter.url.replace("'", "\\'").replace("\"", "\\\"")
            val result = executePluginMethod("plugin.parseChapter('$path')")
            // For novels, the result is HTML content - return as a single text page
            // Store the chapter URL in the page so fetchPageText can re-fetch if needed
            listOf(Page(0, chapter.url, "", text = decodeJsonStringIfQuoted(result)))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error in getPageList for ${plugin.name}" }
            emptyList()
        }
    }

    override fun getFilterList(): FilterList {
        return try {
            // getFilterList is synchronous in the interface, but we need to run suspend code.
            // We use runBlocking here.
            runBlocking {
                val result = executePluginMethod("plugin.filters || {}")
                parseFiltersFromJson(result)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error getting filters for ${plugin.name}" }
            FilterList()
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Read plugin's pluginSettings and create preferences backed by persistent storage
        try {
            val result = runBlocking { executePluginMethod("JSON.stringify(plugin.pluginSettings || {})") }
            val settingsJson = decodeJsonStringIfQuoted(result)
            if (settingsJson.isBlank() || settingsJson == "{}" || settingsJson == "null") return

            val settings = json.parseToJsonElement(settingsJson).jsonObject
            val prefs = screen.context.getSharedPreferences("jsplugin_storage_$pluginId", android.content.Context.MODE_PRIVATE)

            settings.forEach { (key, value) ->
                val settingObj = value as? JsonObject ?: return@forEach
                val label = settingObj["label"]?.jsonPrimitive?.content ?: key
                val type = settingObj["type"]?.jsonPrimitive?.content ?: "Switch"

                when (type) {
                    "Switch" -> {
                        val storedValue = prefs.getString(key, "")?.toBooleanStrictOrNull() ?: false
                        logcat(LogPriority.DEBUG) { "[$pluginId] Switch pref '$key' loaded: $storedValue" }
                        val pref = androidx.preference.SwitchPreferenceCompat(screen.context).apply {
                            // Set isPersistent=false BEFORE key to prevent framework auto-load
                            this.isPersistent = false
                            this.key = key
                            this.title = label
                            this.setDefaultValue(storedValue)
                            setOnPreferenceChangeListener { _, newValue ->
                                val boolVal = newValue as? Boolean ?: false
                                prefs.edit().putString(key, boolVal.toString()).apply()
                                logcat(LogPriority.DEBUG) { "[${pluginId}] Switch pref '$key' changed: $boolVal" }
                                true
                            }
                        }
                        screen.addPreference(pref)
                        // Set isChecked AFTER addPreference so onSetInitialValue doesn't override it
                        pref.isChecked = storedValue
                    }
                    "Text" -> {
                        val defaultValue = settingObj["value"]?.jsonPrimitive?.content ?: ""
                        val storedText = prefs.getString(key, defaultValue) ?: defaultValue
                        val pref = androidx.preference.EditTextPreference(screen.context).apply {
                            // Set isPersistent=false BEFORE key to prevent framework auto-load
                            this.isPersistent = false
                            this.key = key
                            this.title = label
                            this.setDefaultValue(defaultValue)
                            setOnPreferenceChangeListener { p, newValue ->
                                val strVal = newValue.toString()
                                prefs.edit().putString(key, strVal).apply()
                                (p as? androidx.preference.EditTextPreference)?.summary = strVal
                                true
                            }
                        }
                        screen.addPreference(pref)
                        // Set text/summary AFTER addPreference so onSetInitialValue doesn't override
                        pref.text = storedText
                        pref.summary = storedText
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error setting up preference screen for ${plugin.name}" }
        }
    }

    private fun anyToJsonElement(any: Any?): JsonElement {
        return when (any) {
            null -> kotlinx.serialization.json.JsonNull
            is Boolean -> JsonPrimitive(any)
            is Number -> JsonPrimitive(any)
            is String -> JsonPrimitive(any)
            is List<*> -> JsonArray(any.map { anyToJsonElement(it) })
            is Map<*, *> -> JsonObject(any.entries.associate { (k, v) -> (k.toString()) to anyToJsonElement(v) })
            else -> JsonPrimitive(any.toString())
        }
    }

    private fun decodeJsonStringIfQuoted(jsonValue: String): String {
        if (jsonValue.isBlank() || jsonValue == "null") return ""
        return try {
            val el = json.parseToJsonElement(jsonValue)
            if (el is JsonPrimitive && el.isString) el.content else jsonValue
        } catch (_: Exception) {
            jsonValue
        }
    }


    // Parsing helpers

    private fun parseMangasPage(jsonResult: String, page: Int): MangasPage {
        if (jsonResult == "null" || jsonResult.isBlank()) {
            return MangasPage(emptyList(), false)
        }

        try {
            val array = json.parseToJsonElement(jsonResult).jsonArray
            val mangas = array.mapNotNull { item ->
                try {
                    val obj = item.jsonObject
                    SManga.create().apply {
                        title = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        url = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        // Ensure thumbnail_url is a valid URL or null
                        val coverUrl = obj["cover"]?.jsonPrimitive?.content
                        thumbnail_url = when {
                            coverUrl.isNullOrBlank() || coverUrl == "null" -> null
                            coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> coverUrl
                            coverUrl.startsWith("/") -> baseUrl + coverUrl // baseUrl has no trailing slash
                            else -> "$baseUrl/$coverUrl" // Add slash between baseUrl and relative path
                        }
                        author = obj["author"]?.jsonPrimitive?.content
                    }
                } catch (e: Exception) {
                    null
                }
            }
            // Assume more pages if we got results
            return MangasPage(mangas, mangas.size >= 20)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse mangas: $jsonResult" }
            return MangasPage(emptyList(), false)
        }
    }

    private fun parseNovelDetails(jsonResult: String, existing: SManga): SManga {
        if (jsonResult == "null" || jsonResult.isBlank()) {
            return existing
        }

        try {
            val obj = json.parseToJsonElement(jsonResult).jsonObject
            return SManga.create().apply {
                url = existing.url
                title = obj["name"]?.jsonPrimitive?.content ?: existing.title
                author = obj["author"]?.jsonPrimitive?.content ?: existing.author
                artist = obj["artist"]?.jsonPrimitive?.content
                description = obj["summary"]?.jsonPrimitive?.content
                genre = obj["genres"]?.jsonPrimitive?.content
                // Validate cover URL
                val coverUrl = obj["cover"]?.jsonPrimitive?.content
                thumbnail_url = when {
                    coverUrl.isNullOrBlank() || coverUrl == "null" -> existing.thumbnail_url
                    coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> coverUrl
                    coverUrl.startsWith("/") -> baseUrl + coverUrl
                    else -> "$baseUrl/$coverUrl"
                }
                status = when (obj["status"]?.jsonPrimitive?.content) {
                    "Ongoing" -> SManga.ONGOING
                    "Completed" -> SManga.COMPLETED
                    "On Hiatus", "OnHiatus" -> SManga.ON_HIATUS
                    "Cancelled" -> SManga.CANCELLED
                    "Licensed" -> SManga.LICENSED
                    else -> SManga.UNKNOWN
                }
                initialized = true
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse novel details: $jsonResult" }
            return existing
        }
    }

    private fun parseChapterList(jsonResult: String): List<SChapter> {
        if (jsonResult == "null" || jsonResult.isBlank()) {
            return emptyList()
        }

        try {
            val obj = json.parseToJsonElement(jsonResult).jsonObject
            val chaptersArray = obj["chapters"]?.jsonArray ?: return emptyList()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            // Return chapters in source order (newest-first from LNReader);
            // reversal and chapter_number assignment happen in getChapterList() after all pages are collected.
            return chaptersArray.mapIndexedNotNull { index, item ->
                try {
                    val chapterObj = item.jsonObject
                    SChapter.create().apply {
                        name = chapterObj["name"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                        url = chapterObj["path"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                        // Keep plugin-provided chapterNumber if available; otherwise leave as -1
                        // (global numbering is assigned later in getChapterList)
                        chapterObj["chapterNumber"]?.jsonPrimitive?.content?.toFloatOrNull()?.let {
                            chapter_number = it
                        }
                        date_upload = try {
                            chapterObj["releaseTime"]?.jsonPrimitive?.content?.let { dateStr ->
                                if (dateStr.contains("T")) {
                                    // ISO format
                                    java.time.Instant.parse(dateStr).toEpochMilli()
                                } else {
                                    dateFormat.parse(dateStr)?.time ?: 0L
                                }
                            } ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                        scanlator = chapterObj["page"]?.jsonPrimitive?.content // Volume info
                    }
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse chapters: $jsonResult" }
            return emptyList()
        }
    }

    /**
     * Parse chapters from a parsePage() result.
     * parsePage returns { chapters: [...] } like parseNovel, but just for one page.
     * Returns chapters in source order (newest-first from LNReader);
     * reversal and chapter_number assignment happen in getChapterList().
     */
    private fun parsePageChapters(jsonResult: String): List<SChapter> {
        if (jsonResult == "null" || jsonResult.isBlank()) return emptyList()
        try {
            val element = json.parseToJsonElement(jsonResult)
            val chaptersArray = when (element) {
                is JsonObject -> element["chapters"]?.jsonArray ?: return emptyList()
                is JsonArray -> element // Some plugins return a plain array
                else -> return emptyList()
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return chaptersArray.mapIndexedNotNull { index, item ->
                try {
                    val chapterObj = item.jsonObject
                    SChapter.create().apply {
                        name = chapterObj["name"]?.jsonPrimitive?.content ?: "Chapter ${index + 1}"
                        url = chapterObj["path"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                        // Keep plugin-provided chapterNumber if available; otherwise leave as -1
                        // (global numbering is assigned later in getChapterList)
                        chapterObj["chapterNumber"]?.jsonPrimitive?.content?.toFloatOrNull()?.let {
                            chapter_number = it
                        }
                        date_upload = try {
                            chapterObj["releaseTime"]?.jsonPrimitive?.content?.let { dateStr ->
                                if (dateStr.contains("T")) java.time.Instant.parse(dateStr).toEpochMilli()
                                else dateFormat.parse(dateStr)?.time ?: 0L
                            } ?: 0L
                        } catch (_: Exception) { 0L }
                        scanlator = chapterObj["page"]?.jsonPrimitive?.content
                    }
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse page chapters: $jsonResult" }
            return emptyList()
        }
    }

    private fun parseFiltersFromJson(jsonResult: String): FilterList {
        if (jsonResult == "null" || jsonResult.isBlank() || jsonResult == "{}" || jsonResult == "[]") {
            return FilterList()
        }

        try {
            val element = json.parseToJsonElement(jsonResult)
            val filters = mutableListOf<Filter<*>>()

            when (element) {
                is JsonObject -> {
                    // Object format: { "filterKey": { type: ..., label: ..., options: ... } }
                    element.forEach { (key, value) ->
                        val filterObj = value as? JsonObject ?: return@forEach
                        parseFilterObject(key, filterObj, filters)
                    }
                }
                is JsonArray -> {
                    // Array format: [ { type: ..., label: ..., options: ... }, ... ]
                    element.forEachIndexed { index, value ->
                        val filterObj = value as? JsonObject ?: return@forEachIndexed
                        parseFilterObject("filter_$index", filterObj, filters)
                    }
                }
                else -> {
                    // Invalid format
                    return FilterList()
                }
            }

            return FilterList(filters)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse filters: $jsonResult" }
            return FilterList()
        }
    }

    // Custom filter classes that store both label and value

    /** Picker filter that stores label-value pairs */
    class JsSelectFilter(
        name: String,
        val key: String,
        val options: List<Pair<String, String>>, // Pair of (label, value)
        defaultIndex: Int = 0,
    ) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), defaultIndex) {
        /** Get the selected option's value (not label) */
        fun selectedValue(): String = options.getOrNull(state)?.second ?: ""
    }

    /** Checkbox group filter where each checkbox has a label and value */
    class JsCheckboxGroup(
        name: String,
        val key: String,
        val checkboxes: List<JsCheckbox>,
    ) : Filter.Group<JsCheckbox>(name, checkboxes) {
        /** Get list of selected values */
        fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
    }

    /** Single checkbox with associated value */
    class JsCheckbox(
        val label: String,
        val value: String,
    ) : Filter.CheckBox(label)

    /** TriState checkbox group for include/exclude functionality */
    class JsTriStateGroup(
        name: String,
        val key: String,
        val triStates: List<JsTriState>,
    ) : Filter.Group<JsTriState>(name, triStates) {
        /** Get included values */
        fun includedValues(): List<String> = state.filter { it.isIncluded() }.map { it.value }
        /** Get excluded values */
        fun excludedValues(): List<String> = state.filter { it.isExcluded() }.map { it.value }
    }

    /** Single TriState checkbox with associated value */
    class JsTriState(
        val label: String,
        val value: String,
    ) : Filter.TriState(label)

    /** Text input filter that stores the original JSON key */
    class JsTextFilter(
        name: String,
        val key: String,
        defaultValue: String = "",
    ) : Filter.Text(name) {
        init { state = defaultValue }
    }

    /** Switch filter that stores the original JSON key */
    class JsSwitchFilter(
        name: String,
        val key: String,
        defaultValue: Boolean = false,
    ) : Filter.CheckBox(name, defaultValue)

    private fun parseFilterObject(key: String, filterObj: JsonObject, filters: MutableList<Filter<*>>) {
        val type = filterObj["type"]?.jsonPrimitive?.content
        val label = filterObj["label"]?.jsonPrimitive?.content ?: key

        when (type) {
            "Text" -> {
                val defaultValue = filterObj["value"]?.jsonPrimitive?.content ?: ""
                filters.add(JsTextFilter(label, key, defaultValue))
            }

            "Picker" -> {
                // Parse options as label-value pairs
                val options = filterObj["options"]?.jsonArray?.mapNotNull { optionEl ->
                    when (optionEl) {
                        is JsonPrimitive -> Pair(optionEl.content, optionEl.content)
                        is JsonObject -> {
                            val optLabel = optionEl["label"]?.jsonPrimitive?.content ?: ""
                            val optValue = optionEl["value"]?.jsonPrimitive?.content ?: optLabel
                            if (optLabel.isNotEmpty()) Pair(optLabel, optValue) else null
                        }
                        else -> null
                    }
                } ?: emptyList()

                // Get default value and find its index
                val defaultValue = filterObj["value"]?.jsonPrimitive?.content
                val defaultIndex = if (defaultValue != null) {
                    options.indexOfFirst { it.second == defaultValue }.takeIf { it >= 0 } ?: 0
                } else 0

                filters.add(JsSelectFilter(label, key, options, defaultIndex))
            }

            "Checkbox" -> {
                // CheckboxGroup: array of checkboxes
                val options = filterObj["options"]?.jsonArray?.mapNotNull { optionEl ->
                    when (optionEl) {
                        is JsonPrimitive -> JsCheckbox(optionEl.content, optionEl.content)
                        is JsonObject -> {
                            val optLabel = optionEl["label"]?.jsonPrimitive?.content ?: ""
                            val optValue = optionEl["value"]?.jsonPrimitive?.content ?: optLabel
                            if (optLabel.isNotEmpty()) JsCheckbox(optLabel, optValue) else null
                        }
                        else -> null
                    }
                } ?: emptyList()

                // Get default selected values
                val defaultValues = filterObj["value"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive?.content
                }?.toSet() ?: emptySet()

                // Set initial state for checkboxes
                options.forEach { checkbox ->
                    checkbox.state = checkbox.value in defaultValues
                }

                filters.add(JsCheckboxGroup(label, key, options))
            }

            "Switch" -> {
                val defaultValue = filterObj["value"]?.jsonPrimitive?.let {
                    it.content.toBooleanStrictOrNull() ?: (it.content == "true")
                } ?: false
                filters.add(JsSwitchFilter(label, key, defaultValue))
            }

            "XCheckbox" -> {
                // ExcludableCheckboxGroup: TriState checkboxes for include/exclude
                val options = filterObj["options"]?.jsonArray?.mapNotNull { optionEl ->
                    when (optionEl) {
                        is JsonPrimitive -> JsTriState(optionEl.content, optionEl.content)
                        is JsonObject -> {
                            val optLabel = optionEl["label"]?.jsonPrimitive?.content ?: ""
                            val optValue = optionEl["value"]?.jsonPrimitive?.content ?: optLabel
                            if (optLabel.isNotEmpty()) JsTriState(optLabel, optValue) else null
                        }
                        else -> null
                    }
                } ?: emptyList()

                // Get default include/exclude values
                val defaultValueObj = filterObj["value"]?.jsonObject
                val includeValues = defaultValueObj?.get("include")?.jsonArray?.mapNotNull {
                    it.jsonPrimitive?.content
                }?.toSet() ?: emptySet()
                val excludeValues = defaultValueObj?.get("exclude")?.jsonArray?.mapNotNull {
                    it.jsonPrimitive?.content
                }?.toSet() ?: emptySet()

                // Set initial state using Filter.TriState constants
                options.forEach { triState ->
                    triState.state = when (triState.value) {
                        in includeValues -> Filter.TriState.STATE_INCLUDE
                        in excludeValues -> Filter.TriState.STATE_EXCLUDE
                        else -> Filter.TriState.STATE_IGNORE
                    }
                }

                filters.add(JsTriStateGroup(label, key, options))
            }
        }
    }

    // NovelSource implementation
    override suspend fun fetchPageText(page: Page): String = withContext(Dispatchers.IO) {
        try {
            // If the page already has text content (set by getPageList), return it directly
            if (!page.text.isNullOrBlank()) {
                return@withContext page.text!!
            }

            // Validate URL before calling plugin - avoid fetching base URL with empty path
            val chapterUrl = page.url.replace("'", "\\'").replace("\"", "\\\"")
            if (chapterUrl.isBlank()) {
                logcat(LogPriority.WARN) { "[$id] fetchPageText: page.url is blank, cannot parse chapter" }
                return@withContext "Chapter content unavailable (empty URL)"
            }

            val result = executePluginMethod("plugin.parseChapter('$chapterUrl')")

            // Parse result which might be a string or JSON object
            if (result.startsWith("{")) {
                try {
                    val obj = json.parseToJsonElement(result).jsonObject
                    // Try different fields that plugins might use
                    obj["chapterText"]?.jsonPrimitive?.content
                        ?: obj["text"]?.jsonPrimitive?.content
                        ?: obj["content"]?.jsonPrimitive?.content
                        ?: result
                } catch (e: Exception) {
                    result
                }
            } else {
                decodeJsonStringIfQuoted(result)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error fetching page text for ${plugin.name}" }
            "Error loading chapter content: ${e.message}"
        }
    }
}


