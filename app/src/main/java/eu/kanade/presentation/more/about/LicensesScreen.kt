package eu.kanade.presentation.more.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R

@Composable
fun LicensesScreen(
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(R.string.licenses),
                navigateUp = navigateUp,
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
        )
    }
}
