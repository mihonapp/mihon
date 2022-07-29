package eu.kanade.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

data class Extensions(
    val updates: List<Extension.Installed>,
    val installed: List<Extension.Installed>,
    val available: List<Extension.Available>,
    val untrusted: List<Extension.Untrusted>,
)
