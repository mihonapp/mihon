package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import nucleus.presenter.Presenter

/**
 * Compose controller with a Nucleus presenter.
 */
abstract class ComposeController<P : Presenter<*>>(bundle: Bundle? = null) : NucleusController<ComposeControllerBinding, P>(bundle) {

    override fun createBinding(inflater: LayoutInflater): ComposeControllerBinding =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.setContent {
            val nestedScrollInterop = rememberNestedScrollInteropConnection(binding.root)
            TachiyomiTheme {
                ComposeContent(nestedScrollInterop)
            }
        }
    }

    @Composable abstract fun ComposeContent(nestedScrollInterop: NestedScrollConnection)
}

/**
 * Basic Compose controller without a presenter.
 */
abstract class BasicComposeController : BaseController<ComposeControllerBinding>() {

    override fun createBinding(inflater: LayoutInflater): ComposeControllerBinding =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.setContent {
            val nestedScrollInterop = rememberNestedScrollInteropConnection(binding.root)
            TachiyomiTheme {
                ComposeContent(nestedScrollInterop)
            }
        }
    }

    @Composable abstract fun ComposeContent(nestedScrollInterop: NestedScrollConnection)
}

abstract class SearchableComposeController<P : BasePresenter<*>>(bundle: Bundle? = null) : SearchableNucleusController<ComposeControllerBinding, P>(bundle) {

    override fun createBinding(inflater: LayoutInflater): ComposeControllerBinding =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.setContent {
            val nestedScrollInterop = rememberNestedScrollInteropConnection(binding.root)
            TachiyomiTheme {
                ComposeContent(nestedScrollInterop)
            }
        }
    }

    @Composable abstract fun ComposeContent(nestedScrollInterop: NestedScrollConnection)
}
