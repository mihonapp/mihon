package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.LocalBackStack
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import kotlinx.serialization.Serializable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Serializable
data object OpenSourceLicensesRoute : NavKey

@Composable
fun OpenSourceLicensesScreen() {
    val backStack = LocalBackStack.current
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.licenses),
                navigateUp = backStack::removeLastOrNull,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        val libraries by produceLibraries(R.raw.aboutlibraries)
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            detailMode = LibraryDetailMode.Sheet,
        )
    }
}
