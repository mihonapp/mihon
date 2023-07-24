package eu.kanade.tachiyomi.ui.browse.migration.sources

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen

@Composable
fun Screen.migrateSourceTab(): TabContent {
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MigrateSourceScreenModel() }
    val state by screenModel.state.collectAsState()

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
        content = { contentPadding, _ ->
            MigrateSourceScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source ->
                    navigator.push(MigrateMangaScreen(source.id))
                },
                onToggleSortingDirection = screenModel::toggleSortingDirection,
                onToggleSortingMode = screenModel::toggleSortingMode,
            )
        },
    )
}
