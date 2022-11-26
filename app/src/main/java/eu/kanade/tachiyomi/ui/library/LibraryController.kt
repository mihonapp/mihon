package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryController(
    bundle: Bundle? = null,
) : BasicFullComposeController(bundle), RootController {

    /**
     * Sheet containing filter/sort/display items.
     */
    private var settingsSheet: LibrarySettingsSheet? = null

    @Composable
    override fun ComposeContent() {
        Navigator(screen = LibraryScreen)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        settingsSheet = LibrarySettingsSheet(router)
        viewScope.launch {
            LibraryScreen.openSettingsSheetEvent
                .collectLatest(::showSettingsSheet)
        }
    }

    override fun onDestroyView(view: View) {
        settingsSheet?.sheetScope?.cancel()
        settingsSheet = null
        super.onDestroyView(view)
    }

    fun showSettingsSheet(category: Category? = null) {
        if (category != null) {
            settingsSheet?.show(category)
        } else {
            viewScope.launch { LibraryScreen.requestOpenSettingsSheet() }
        }
    }

    fun search(query: String) = LibraryScreen.search(query)
}
