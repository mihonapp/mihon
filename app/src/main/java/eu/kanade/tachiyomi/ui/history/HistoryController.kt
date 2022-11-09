package eu.kanade.tachiyomi.ui.history

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.history.interactor.GetNextChapters
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HistoryController : BasicFullComposeController(), RootController {

    @Composable
    override fun ComposeContent() {
        Navigator(screen = HistoryScreen)
    }

    fun resumeLastChapterRead() {
        val context = activity ?: return
        viewScope.launchIO {
            val chapter = Injekt.get<GetNextChapters>().await(onlyUnread = false).firstOrNull()
            HistoryScreen.openChapter(context, chapter)
        }
    }
}
