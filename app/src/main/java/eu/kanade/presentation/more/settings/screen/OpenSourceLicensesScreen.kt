package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import tachiyomi.presentation.core.components.material.Scaffold

class OpenSourceLicensesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(R.string.licenses),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            LibrariesContainer(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = contentPadding,
                colors = LibraryDefaults.libraryColors(
                    backgroundColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    badgeBackgroundColor = MaterialTheme.colorScheme.primary,
                    badgeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                onLibraryClick = {
                    val libraryLicenseScreen = OpenSourceLibraryLicenseScreen(
                        name = it.name,
                        website = it.website,
                        license = it.licenses.firstOrNull()?.htmlReadyLicenseContent.orEmpty(),
                    )
                    navigator.push(libraryLicenseScreen)
                },
            )
        }
    }
}
