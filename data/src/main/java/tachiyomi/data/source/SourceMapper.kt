package tachiyomi.data.source

import tachiyomi.domain.source.model.SourceData

val sourceDataMapper: (Long, String, String) -> SourceData = { id, lang, name ->
    SourceData(id, lang, name)
}
