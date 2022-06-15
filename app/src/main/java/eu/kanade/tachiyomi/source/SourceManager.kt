package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.domain.source.interactor.GetSourceData
import eu.kanade.domain.source.interactor.UpsertSourceData
import eu.kanade.domain.source.model.SourceData
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.lang.launchIO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import rx.Observable
import tachiyomi.source.model.ChapterInfo
import tachiyomi.source.model.MangaInfo
import uy.kohesive.injekt.injectLazy

class SourceManager(private val context: Context) {

    private val extensionManager: ExtensionManager by injectLazy()
    private val getSourceData: GetSourceData by injectLazy()
    private val upsertSourceData: UpsertSourceData by injectLazy()

    private val sourcesMap = mutableMapOf<Long, Source>()
    private val stubSourcesMap = mutableMapOf<Long, StubSource>()

    private val _catalogueSources: MutableStateFlow<List<CatalogueSource>> = MutableStateFlow(listOf())
    val catalogueSources: Flow<List<CatalogueSource>> = _catalogueSources
    val onlineSources: Flow<List<HttpSource>> =
        _catalogueSources.map { sources -> sources.filterIsInstance<HttpSource>() }

    init {
        createInternalSources().forEach { registerSource(it) }
    }

    fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    fun getOnlineSources() = sourcesMap.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMap.values.filterIsInstance<CatalogueSource>()

    fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    internal fun registerSource(source: Source) {
        if (!sourcesMap.containsKey(source.id)) {
            sourcesMap[source.id] = source
        }
        registerStubSource(source.toSourceData())
        triggerCatalogueSources()
    }

    private fun registerStubSource(sourceData: SourceData) {
        launchIO {
            val dbSourceData = getSourceData.await(sourceData.id)

            if (dbSourceData != sourceData) {
                upsertSourceData.await(sourceData)
            }
            if (stubSourcesMap[sourceData.id]?.toSourceData() != sourceData) {
                stubSourcesMap[sourceData.id] = StubSource(sourceData)
            }
        }
    }

    internal fun unregisterSource(source: Source) {
        sourcesMap.remove(source.id)
        triggerCatalogueSources()
    }

    private fun triggerCatalogueSources() {
        _catalogueSources.update {
            sourcesMap.values.filterIsInstance<CatalogueSource>()
        }
    }

    private fun createInternalSources(): List<Source> = listOf(
        LocalSource(context),
    )

    private suspend fun createStubSource(id: Long): StubSource {
        getSourceData.await(id)?.let {
            return StubSource(it)
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return StubSource(it)
        }
        return StubSource(SourceData(id, "", ""))
    }

    @Suppress("OverridingDeprecatedMember")
    open inner class StubSource(val sourceData: SourceData) : Source {

        override val name: String = sourceData.name

        override val lang: String = sourceData.lang

        override val id: Long = sourceData.id

        override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
            throw getSourceNotInstalledException()
        }

        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getChapterList(manga: MangaInfo): List<ChapterInfo> {
            throw getSourceNotInstalledException()
        }

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getPageList(chapter: ChapterInfo): List<tachiyomi.source.model.Page> {
            throw getSourceNotInstalledException()
        }

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            if (name.isNotBlank() && lang.isNotBlank()) {
                return "$name (${lang.uppercase()})"
            }
            return id.toString()
        }

        fun getSourceNotInstalledException(): SourceNotInstalledException {
            return SourceNotInstalledException(toString())
        }
    }

    inner class SourceNotInstalledException(val sourceString: String) :
        Exception(context.getString(R.string.source_not_installed, sourceString))
}
