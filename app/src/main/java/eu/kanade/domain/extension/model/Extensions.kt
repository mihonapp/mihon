package eu.kanade.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

data class Extensions(
    val updates: List<Extension.Loaded>,
    val loaded: List<Extension.Loaded>,
    val available: List<Extension.Available>,
    val notLoaded: List<Extension.NotLoaded>,
)
