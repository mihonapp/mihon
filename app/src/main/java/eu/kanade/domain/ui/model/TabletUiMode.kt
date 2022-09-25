package eu.kanade.domain.ui.model

import eu.kanade.tachiyomi.R

enum class TabletUiMode(val titleResId: Int) {
    AUTOMATIC(R.string.automatic_background),
    ALWAYS(R.string.lock_always),
    LANDSCAPE(R.string.landscape),
    NEVER(R.string.lock_never),
}
