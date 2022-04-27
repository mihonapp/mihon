package eu.kanade.data.source

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.source.CatalogueSource

val sourceMapper: (eu.kanade.tachiyomi.source.Source) -> Source = { source ->
    Source(
        source.id,
        source.lang,
        source.name,
        false
    )
}

val catalogueSourceMapper: (CatalogueSource) -> Source = { source ->
    sourceMapper(source).copy(supportsLatest = source.supportsLatest)
}
