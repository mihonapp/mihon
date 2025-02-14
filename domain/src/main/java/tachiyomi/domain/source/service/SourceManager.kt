package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    val isInitialized: StateFlow<Boolean>

    val sources: Flow<List<Source>>

    fun get(sourceKey: Long): Source?

    fun getOrStub(sourceKey: Long): Source

    fun getSources(): List<Source>

    fun getStubSources(): List<StubSource>

    fun getOnlineSources(): List<HttpSource>
}
