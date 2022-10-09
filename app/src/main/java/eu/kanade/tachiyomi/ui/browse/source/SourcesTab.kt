package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController

@Composable
fun sourcesTab(
    router: Router?,
    presenter: SourcesPresenter,
) = TabContent(
    titleRes = R.string.label_sources,
    actions = listOf(
        AppBar.Action(
            title = stringResource(R.string.action_global_search),
            icon = Icons.Outlined.TravelExplore,
            onClick = { router?.pushController(GlobalSearchController()) },
        ),
        AppBar.Action(
            title = stringResource(R.string.action_filter),
            icon = Icons.Outlined.FilterList,
            onClick = { router?.pushController(SourceFilterController()) },
        ),
    ),
    content = { contentPadding ->
        SourcesScreen(
            presenter = presenter,
            contentPadding = contentPadding,
            onClickItem = { source, query ->
                presenter.onOpenSource(source)
                router?.pushController(BrowseSourceController(source, query))
            },
            onClickDisable = { source ->
                presenter.toggleSource(source)
            },
            onClickPin = { source ->
                presenter.togglePin(source)
            },
        )
    },
)
