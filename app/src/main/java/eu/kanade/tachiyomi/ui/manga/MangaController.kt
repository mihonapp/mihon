package eu.kanade.tachiyomi.ui.manga

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController

class MangaController : BasicFullComposeController {

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getLong(MANGA_EXTRA))

    constructor(
        mangaId: Long,
        fromSource: Boolean = false,
    ) : super(bundleOf(MANGA_EXTRA to mangaId, FROM_SOURCE_EXTRA to fromSource))

    val mangaId: Long
        get() = args.getLong(MANGA_EXTRA)

    val fromSource: Boolean
        get() = args.getBoolean(FROM_SOURCE_EXTRA)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = MangaScreen(mangaId, fromSource))
    }

    companion object {
        const val FROM_SOURCE_EXTRA = "from_source"
        const val MANGA_EXTRA = "manga"
    }
}
