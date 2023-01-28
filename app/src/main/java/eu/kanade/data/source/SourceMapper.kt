package eu.kanade.data.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import tachiyomi.domain.source.model.Source

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
