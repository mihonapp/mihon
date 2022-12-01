package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.util.system.getSerializableCompat

class SourceSearchController(bundle: Bundle) : BasicFullComposeController(bundle) {

    constructor(manga: Manga? = null, source: CatalogueSource, searchQuery: String? = null) : this(
        bundleOf(
            SOURCE_ID_KEY to source.id,
            MANGA_KEY to manga,
            SEARCH_QUERY_KEY to searchQuery,
        ),
    )

    private var oldManga: Manga = args.getSerializableCompat(MANGA_KEY)!!
    private val sourceId = args.getLong(SOURCE_ID_KEY)
    private val query = args.getString(SEARCH_QUERY_KEY)

    @Composable
    override fun ComposeContent() {
        Navigator(screen = SourceSearchScreen(oldManga, sourceId, query))
    }
}

private const val MANGA_KEY = "oldManga"
private const val SOURCE_ID_KEY = "sourceId"
private const val SEARCH_QUERY_KEY = "searchQuery"
