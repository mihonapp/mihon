package mihon.extension.host

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.ipc.decodeSourcePreferenceValue
import mihon.extension.ipc.decodeSourcePreferences
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.source.model.FilterList as TFilterList
import eu.kanade.tachiyomi.source.model.MangasPage as TMangasPage
import eu.kanade.tachiyomi.source.model.Page as TPage
import eu.kanade.tachiyomi.source.model.SChapter as TSChapter
import eu.kanade.tachiyomi.source.model.SManga as TSManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate as TSMangaUpdate

class ExtensionSourcePreferenceHostTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private class FakeConfigurableSource(
        private val preferences: SharedPreferences,
        private val enabledByDefault: Boolean = false,
    ) : CatalogueSource, ConfigurableSource {

        override val id: Long = 8801L
        override val name: String = "Fake Configurable Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        var setupCount: Int = 0
        var lastCheckBox: CheckBoxPreference? = null
        var listenerValues: MutableList<Any> = mutableListOf()

        override fun getSourcePreferences(): SharedPreferences = preferences

        override fun setupPreferenceScreen(screen: PreferenceScreen) {
            setupCount++
            val context = screen.context
            val checkBox = CheckBoxPreference(context).apply {
                key = "dataSaver"
                title = "Data Saver"
                summary = "Reduce data usage"
                defaultValue = enabledByDefault
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    listenerValues += newValue
                    true
                }
            }
            lastCheckBox = checkBox
            screen.addPreference(checkBox)
            screen.addPreference(
                EditTextPreference(context).apply {
                    key = "apiKey"
                    title = "API Key"
                    defaultValue = "default-key"
                    text = "default-key"
                },
            )
            screen.addPreference(
                EditTextPreference(context).apply {
                    key = "pageLimit"
                    title = "Page Limit"
                    defaultValue = 20
                },
            )
            screen.addPreference(
                EditTextPreference(context).apply {
                    key = "timeout"
                    title = "Timeout"
                    defaultValue = 1_000L
                },
            )
            screen.addPreference(
                EditTextPreference(context).apply {
                    key = "threshold"
                    title = "Threshold"
                    defaultValue = 1.5f
                },
            )
            screen.addPreference(
                ListPreference(context).apply {
                    key = "quality"
                    title = "Quality"
                    entries = arrayOf("Low", "High")
                    entryValues = arrayOf("low", "high")
                    defaultValue = "low"
                    value = "low"
                },
            )
            screen.addPreference(
                MultiSelectListPreference(context).apply {
                    key = "genres"
                    title = "Genres"
                    entries = arrayOf("Action", "Comedy")
                    entryValues = arrayOf("action", "comedy")
                    if (enabledByDefault) defaultValue = setOf("action", "comedy")
                    values = setOf("action")
                },
            )
            screen.addPreference(
                object : Preference(context) {
                    init {
                        key = "custom"
                        title = "Custom Preference"
                    }
                },
            )
        }

        override suspend fun getSearchManga(page: Int, query: String, filters: TFilterList): TMangasPage {
            val dataSaver = preferences.getBoolean("dataSaver", false)
            return TMangasPage(
                mangas = listOf(
                    TSManga.create().apply {
                        url = "/search"
                        title = if (dataSaver) "saver-on" else "saver-off"
                    },
                ),
                hasNextPage = false,
            )
        }

        override suspend fun getMangaUpdate(
            manga: TSManga,
            chapters: List<TSChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): TSMangaUpdate = TSMangaUpdate(manga, chapters)

        override suspend fun getPageList(chapter: TSChapter): List<TPage> = emptyList()
    }

    private fun preferencesFor(sourceId: Long): SharedPreferences {
        return Application().getSharedPreferences("source_$sourceId", Context.MODE_PRIVATE)
    }

    private fun decodePreferences(payloadJson: String): SourcePreferencesDto {
        return decodeSourcePreferences(payloadJson)
            ?: error("Could not decode source preferences payload: $payloadJson")
    }

    @Test
    fun `unset checkbox and multi select expose declared defaults while stored empty values win`(
        @org.junit.jupiter.api.io.TempDir directory: java.nio.file.Path,
    ) {
        val context = Application(directory.toFile(), "default.preference.test")
        val preferences = context.getSharedPreferences("source_defaults", Context.MODE_PRIVATE)
        val source = FakeConfigurableSource(preferences, enabledByDefault = true)
        val model = SourcePreferenceModel(source, context)
        val defaults = model.definitions().associateBy { it.key }
        defaults.getValue("dataSaver").currentValue shouldBe BooleanPreferenceValueDto(true)
        defaults.getValue("genres").currentValue shouldBe ListPreferenceValueDto(listOf("action", "comedy"))

        preferences.edit().putBoolean("dataSaver", false).putStringSet("genres", emptySet()).commit() shouldBe true
        val stored = model.definitions().associateBy { it.key }
        stored.getValue("dataSaver").currentValue shouldBe BooleanPreferenceValueDto(false)
        stored.getValue("genres").currentValue shouldBe ListPreferenceValueDto(emptyList())
    }

    @Test
    fun `host lists real configurable source preferences including current values and options`(): Unit = runBlocking {
        val sourceId = 8801L
        val preferences = preferencesFor(sourceId)
        preferences.edit()
            .putBoolean("dataSaver", true)
            .putString("apiKey", "secret")
            .putInt("pageLimit", 42)
            .putLong("timeout", 5_000L)
            .putFloat("threshold", 2.5f)
            .putString("quality", "high")
            .putStringSet("genres", setOf("comedy"))
            .apply()

        val fakeSource = FakeConfigurableSource(preferences)
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        engine.registerSource(TachiyomiCatalogueSourceAdapter(fakeSource))

        val first = engine.handleRequest(
            IpcRequest(1, IpcCommands.GET_SOURCE_PREFERENCES, json.encodeToString(SourcePayload(sourceId))),
        )
        first.success shouldBe true
        val firstPreferences = decodePreferences(first.payloadJson)
        firstPreferences.supported shouldBe true

        val byKey = firstPreferences.definitions.associateBy { it.key }
        byKey.getValue("dataSaver").type shouldBe SourcePreferenceTypeDto.BOOLEAN
        byKey.getValue("dataSaver").defaultValue shouldBe BooleanPreferenceValueDto(false)
        byKey.getValue("dataSaver").currentValue shouldBe BooleanPreferenceValueDto(true)

        byKey.getValue("apiKey").type shouldBe SourcePreferenceTypeDto.STRING
        byKey.getValue("apiKey").currentValue shouldBe StringPreferenceValueDto("secret")

        byKey.getValue("pageLimit").type shouldBe SourcePreferenceTypeDto.INT
        byKey.getValue("pageLimit").defaultValue shouldBe IntPreferenceValueDto(20)
        byKey.getValue("pageLimit").currentValue shouldBe IntPreferenceValueDto(42)

        byKey.getValue("timeout").type shouldBe SourcePreferenceTypeDto.LONG
        byKey.getValue("timeout").currentValue shouldBe LongPreferenceValueDto(5_000L)

        byKey.getValue("threshold").type shouldBe SourcePreferenceTypeDto.FLOAT
        byKey.getValue("threshold").currentValue shouldBe FloatPreferenceValueDto(2.5f)

        val quality = byKey.getValue("quality")
        quality.type shouldBe SourcePreferenceTypeDto.SELECT
        quality.currentValue shouldBe SelectPreferenceValueDto("high")
        quality.options.map { it.value } shouldContainExactly listOf("low", "high")

        val genres = byKey.getValue("genres")
        genres.type shouldBe SourcePreferenceTypeDto.LIST
        genres.currentValue shouldBe ListPreferenceValueDto(listOf("comedy"))
        genres.options.map { it.value } shouldContainExactly listOf("action", "comedy")

        val custom = byKey.getValue("custom")
        custom.type shouldBe SourcePreferenceTypeDto.UNKNOWN
        custom.readOnly shouldBe true

        // The preference model is cached per source, so repeated list calls do not re-run setup.
        engine.handleRequest(
            IpcRequest(2, IpcCommands.GET_SOURCE_PREFERENCES, json.encodeToString(SourcePayload(sourceId))),
        )
        fakeSource.setupCount shouldBe 1
    }

    @Test
    fun `host applies set requests to source preference objects and shared preferences`(): Unit = runBlocking {
        val sourceId = 8801L
        val preferences = preferencesFor(sourceId)
        val fakeSource = FakeConfigurableSource(preferences)
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        engine.registerSource(TachiyomiCatalogueSourceAdapter(fakeSource))

        suspend fun setPreference(key: String, value: mihon.extension.ipc.SourcePreferenceValueDto) {
            val response = engine.handleRequest(
                IpcRequest(
                    1,
                    IpcCommands.SET_SOURCE_PREFERENCE,
                    json.encodeToString(SetSourcePreferencePayload(sourceId, key, value)),
                ),
            )
            response.success shouldBe true
        }

        setPreference("dataSaver", BooleanPreferenceValueDto(true))
        preferences.getBoolean("dataSaver", false) shouldBe true
        fakeSource.lastCheckBox?.isChecked shouldBe true
        fakeSource.listenerValues.lastOrNull() shouldBe true

        setPreference("apiKey", StringPreferenceValueDto("new-key"))
        preferences.getString("apiKey", null) shouldBe "new-key"

        setPreference("pageLimit", IntPreferenceValueDto(75))
        preferences.getInt("pageLimit", 0) shouldBe 75

        setPreference("timeout", LongPreferenceValueDto(9_000L))
        preferences.getLong("timeout", 0L) shouldBe 9_000L

        setPreference("threshold", FloatPreferenceValueDto(3.5f))
        preferences.getFloat("threshold", 0f) shouldBe 3.5f

        setPreference("quality", SelectPreferenceValueDto("high"))
        preferences.getString("quality", null) shouldBe "high"

        setPreference("genres", ListPreferenceValueDto(listOf("action", "comedy")))
        preferences.getStringSet("genres", emptySet()) shouldBe setOf("action", "comedy")

        // The updated value is visible to subsequent source operations without restarting the host.
        val search = engine.handleRequest(
            IpcRequest(2, IpcCommands.SEARCH_MANGA, json.encodeToString(SearchPayload(sourceId, 1, "query"))),
        )
        search.success shouldBe true
        val searchPage = json.decodeFromString<mihon.extension.source.model.MangasPage>(search.payloadJson)
        searchPage.mangas.single().title shouldBe "saver-on"

        // Unknown preference types stay visible but cannot be written.
        val unsupported = engine.handleRequest(
            IpcRequest(
                3,
                IpcCommands.SET_SOURCE_PREFERENCE,
                json.encodeToString(
                    SetSourcePreferencePayload(sourceId, "custom", UnknownPreferenceValueDto("value")),
                ),
            ),
        )
        unsupported.success shouldBe false
        unsupported.error.orEmpty().lowercase().contains("read-only") shouldBe true
    }

    @Test
    fun `host reports unsupported sources without dropping their source`(): Unit = runBlocking {
        val engine = ExtensionHostEngine(BrokeredHttpClient { null })
        engine.registerSource(TestNonConfigurableSource())

        val response = engine.handleRequest(
            IpcRequest(1, IpcCommands.GET_SOURCE_PREFERENCES, json.encodeToString(SourcePayload(9901L))),
        )
        response.success shouldBe true
        val preferences = decodePreferences(response.payloadJson)
        preferences.supported shouldBe false
        preferences.definitions.shouldContainExactly(emptyList())

        val setResponse = engine.handleRequest(
            IpcRequest(
                2,
                IpcCommands.SET_SOURCE_PREFERENCE,
                json.encodeToString(
                    SetSourcePreferencePayload(9901L, "dataSaver", BooleanPreferenceValueDto(true)),
                ),
            ),
        )
        setResponse.success shouldBe false
    }

    private class TestNonConfigurableSource : mihon.extension.source.WindowsCatalogueSource {
        override val id: Long = 9901L
        override val name: String = "Non Configurable"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularManga(page: Int) = mihon.extension.source.model.MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int) = mihon.extension.source.model.MangasPage(emptyList(), false)
        override suspend fun searchManga(
            page: Int,
            query: String,
            filters: mihon.extension.source.model.FilterList,
        ) = mihon.extension.source.model.MangasPage(emptyList(), false)

        override suspend fun getMangaDetails(manga: mihon.extension.source.model.SManga) = manga
        override suspend fun getChapterList(
            manga: mihon.extension.source.model.SManga,
        ) = emptyList<mihon.extension.source.model.SChapter>()
        override suspend fun getPageList(chapter: mihon.extension.source.model.SChapter) =
            emptyList<mihon.extension.source.model.Page>()
    }
}
