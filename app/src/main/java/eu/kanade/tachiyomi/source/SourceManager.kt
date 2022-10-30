package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.domain.source.model.SourceData
import eu.kanade.domain.source.repository.SourceDataRepository
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class SourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: SourceDataRepository,
) {
    private val downloadManager: DownloadManager by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map { it.values.filterIsInstance<CatalogueSource>() }
    val onlineSources: Flow<List<HttpSource>> = catalogueSources.map { sources -> sources.filterIsInstance<HttpSource>() }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(mapOf(LocalSource.ID to LocalSource(context)))
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(it.toSourceData())
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = StubSource(it)
                    }
                }
        }
    }

    fun get(sourceKey: Long): Source? {
        return sourcesMapFlow.value[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(sourceData: SourceData) {
        scope.launch {
            val (id, lang, name) = sourceData
            val dbSourceData = sourceRepository.getSourceData(id)
            if (dbSourceData == sourceData) return@launch
            sourceRepository.upsertSourceData(id, lang, name)
            if (dbSourceData != null) {
                downloadManager.renameSource(StubSource(dbSourceData), StubSource(sourceData))
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getSourceData(id)?.let {
            return StubSource(it)
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return StubSource(it)
        }
        return StubSource(SourceData(id, "", ""))
    }

    @Suppress("OverridingDeprecatedMember")
    open inner class StubSource(private val sourceData: SourceData) : Source {

        override val id: Long = sourceData.id

        override val name: String = sourceData.name.ifBlank { id.toString() }

        override val lang: String = sourceData.lang

        override suspend fun getMangaDetails(manga: SManga): SManga {
            throw getSourceNotInstalledException()
        }

        @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getMangaDetails"))
        override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getChapterList(manga: SManga): List<SChapter> {
            throw getSourceNotInstalledException()
        }

        @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getChapterList"))
        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            throw getSourceNotInstalledException()
        }

        @Deprecated("Use the 1.x API instead", replaceWith = ReplaceWith("getPageList"))
        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            return Observable.error(getSourceNotInstalledException())
        }

        override fun toString(): String {
            return if (sourceData.isMissingInfo.not()) "$name (${lang.uppercase()})" else id.toString()
        }

        fun getSourceNotInstalledException(): SourceNotInstalledException {
            return SourceNotInstalledException(toString())
        }
    }

    inner class SourceNotInstalledException(val sourceString: String) :
        Exception(context.getString(R.string.source_not_installed, sourceString))
}
