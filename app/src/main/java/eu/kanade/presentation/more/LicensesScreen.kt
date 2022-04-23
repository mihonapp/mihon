package eu.kanade.presentation.more

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults

@Composable
fun LicensesScreen(
    nestedScrollInterop: NestedScrollConnection,
) {
    LibrariesContainer(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollInterop),
        colors = LibraryDefaults.libraryColors(
            backgroundColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            badgeBackgroundColor = MaterialTheme.colorScheme.primary,
            badgeContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
