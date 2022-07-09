package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController

class CategoryController : FullComposeController<CategoryPresenter>() {

    override fun createPresenter() = CategoryPresenter()

    @Composable
    override fun ComposeContent() {
        CategoryScreen(
            presenter = presenter,
            navigateUp = router::popCurrentController,
        )
    }
}
