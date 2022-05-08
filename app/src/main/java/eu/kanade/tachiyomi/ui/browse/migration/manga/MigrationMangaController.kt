package eu.kanade.tachiyomi.ui.browse.migration.manga

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.core.os.bundleOf
import eu.kanade.presentation.browse.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.base.controller.ComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.search.SearchController
import eu.kanade.tachiyomi.ui.manga.MangaController

class MigrationMangaController : ComposeController<MigrationMangaPresenter> {

    constructor(sourceId: Long, sourceName: String?) : super(
        bundleOf(
            SOURCE_ID_EXTRA to sourceId,
            SOURCE_NAME_EXTRA to sourceName,
        ),
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(
        bundle.getLong(SOURCE_ID_EXTRA),
        bundle.getString(SOURCE_NAME_EXTRA),
    )

    private val sourceId: Long = args.getLong(SOURCE_ID_EXTRA)
    private val sourceName: String? = args.getString(SOURCE_NAME_EXTRA)

    override fun getTitle(): String? = sourceName

    override fun createPresenter(): MigrationMangaPresenter = MigrationMangaPresenter(sourceId)

    @Composable
    override fun ComposeContent(nestedScrollInterop: NestedScrollConnection) {
        MigrateMangaScreen(
            nestedScrollInterop = nestedScrollInterop,
            presenter = presenter,
            onClickItem = {
                router.pushController(SearchController(it.id))
            },
            onClickCover = {
                router.pushController(MangaController(it.id))
            },
        )
    }

    companion object {
        const val SOURCE_ID_EXTRA = "source_id_extra"
        const val SOURCE_NAME_EXTRA = "source_name_extra"
    }
}
