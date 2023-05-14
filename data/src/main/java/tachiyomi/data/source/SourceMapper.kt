package tachiyomi.data.source

import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.model.StubSource

val sourceMapper: (eu.kanade.tachiyomi.source.Source) -> Source = { source ->
    Source(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}

val sourceDataMapper: (Long, String, String) -> StubSource = { id, lang, name ->
    StubSource(id = id, lang = lang, name = name)
}
