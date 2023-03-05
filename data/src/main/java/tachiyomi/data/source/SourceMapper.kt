package tachiyomi.data.source

import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.SourceData

val sourceMapper: (eu.kanade.tachiyomi.source.Source) -> Source = { source ->
    Source(
        source.id,
        source.lang,
        source.name,
        supportsLatest = false,
        isStub = false,
    )
}

val sourceDataMapper: (Long, String, String) -> SourceData = { id, lang, name ->
    SourceData(id, lang, name)
}
