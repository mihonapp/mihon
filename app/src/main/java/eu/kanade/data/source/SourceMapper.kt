package eu.kanade.data.source

import eu.kanade.domain.source.model.Source
import eu.kanade.tachiyomi.source.CatalogueSource

val sourceMapper: (CatalogueSource) -> Source = { source ->
    Source(
        source.id,
        source.lang,
        source.name,
        source.supportsLatest
    )
}
