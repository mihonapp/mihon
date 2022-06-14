package eu.kanade.data.source

import eu.kanade.domain.source.model.Source
import eu.kanade.domain.source.model.SourceData
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager

val sourceMapper: (eu.kanade.tachiyomi.source.Source) -> Source = { source ->
    Source(
        source.id,
        source.lang,
        source.name,
        supportsLatest = false,
        isStub = source is SourceManager.StubSource,
    )
}

val catalogueSourceMapper: (CatalogueSource) -> Source = { source ->
    sourceMapper(source).copy(supportsLatest = source.supportsLatest)
}

val sourceDataMapper: (Long, String, String) -> SourceData = { id, lang, name ->
    SourceData(id, lang, name)
}
