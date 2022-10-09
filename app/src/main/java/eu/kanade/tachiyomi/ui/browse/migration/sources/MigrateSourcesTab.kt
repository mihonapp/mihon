package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.bluelinelabs.conductor.Router
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrationMangaController

@Composable
fun migrateSourcesTab(
    router: Router?,
    presenter: MigrationSourcesPresenter,
): TabContent {
    val uriHandler = LocalUriHandler.current

    return TabContent(
        titleRes = R.string.label_migration,
        actions = listOf(
            AppBar.Action(
                title = stringResource(R.string.migration_help_guide),
                icon = Icons.Outlined.HelpOutline,
                onClick = {
                    uriHandler.openUri("https://tachiyomi.org/help/guides/source-migration/")
                },
            ),
        ),
        content = { contentPadding ->
            MigrateSourceScreen(
                presenter = presenter,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    router?.pushController(
                        MigrationMangaController(
                            source.id,
                            source.name,
                        ),
                    )
                },
            )
        },
    )
}
