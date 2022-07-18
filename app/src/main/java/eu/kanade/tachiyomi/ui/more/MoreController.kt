package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.download.DownloadController
import eu.kanade.tachiyomi.ui.setting.SettingsBackupController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController

class MoreController :
    FullComposeController<MorePresenter>(),
    RootController {

    override fun getTitle() = resources?.getString(R.string.label_more)

    override fun createPresenter() = MorePresenter()

    @Composable
    override fun ComposeContent() {
        MoreScreen(
            presenter = presenter,
            onClickDownloadQueue = { router.pushController(DownloadController()) },
            onClickCategories = { router.pushController(CategoryController()) },
            onClickBackupAndRestore = { router.pushController(SettingsBackupController()) },
            onClickSettings = { router.pushController(SettingsMainController()) },
            onClickAbout = { router.pushController(AboutController()) },
        )
    }

    companion object {
        const val URL_HELP = "https://tachiyomi.org/help/"
    }
}
