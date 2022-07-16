package eu.kanade.presentation.more.about

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.plus
import eu.kanade.tachiyomi.R

@Composable
fun LicensesScreen(
    navigateUp: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            AppBar(
                title = stringResource(R.string.licenses),
                navigateUp = navigateUp,
            )
        },
    ) { paddingValues ->
        LibrariesContainer(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = paddingValues + WindowInsets.navigationBars.asPaddingValues(),
            colors = LibraryDefaults.libraryColors(
                backgroundColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                badgeBackgroundColor = MaterialTheme.colorScheme.primary,
                badgeContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}
