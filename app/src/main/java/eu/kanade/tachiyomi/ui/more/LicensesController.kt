package eu.kanade.tachiyomi.ui.more

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults.libraryColors
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.BasicComposeController

class LicensesController : BasicComposeController() {

    override fun getTitle() = resources?.getString(R.string.licenses)

    @Composable
    override fun ComposeContent() {
        val nestedScrollInterop = rememberNestedScrollInteropConnection(binding.root)

        LibrariesContainer(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInterop),
            colors = libraryColors(
                backgroundColor = MaterialTheme.colorScheme.background,
                contentColor = contentColorFor(MaterialTheme.colorScheme.background),
                badgeBackgroundColor = MaterialTheme.colorScheme.primary,
                badgeContentColor = contentColorFor(MaterialTheme.colorScheme.primary),
            ),
        )
    }
}
