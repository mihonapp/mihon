package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.view.setComposeContent
import nucleus.presenter.Presenter

abstract class FullComposeController<P : Presenter<*>>(bundle: Bundle? = null) :
    NucleusController<ComposeControllerBinding, P>(bundle),
    FullComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            setComposeContent {
                ComposeContent()
            }
        }
    }
}

/**
 * Compose controller with a Nucleus presenter.
 */
abstract class ComposeController<P : Presenter<*>>(bundle: Bundle? = null) :
    NucleusController<ComposeControllerBinding, P>(bundle),
    ComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            setComposeContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                ComposeContent(nestedScrollInterop)
            }
        }
    }
}

/**
 * Basic Compose controller without a presenter.
 */
abstract class BasicComposeController :
    BaseController<ComposeControllerBinding>(),
    ComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            setComposeContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                ComposeContent(nestedScrollInterop)
            }
        }
    }
}

/**
 * Basic Compose controller without a presenter.
 */
abstract class BasicFullComposeController :
    BaseController<ComposeControllerBinding>(),
    FullComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            setComposeContent {
                ComposeContent()
            }
        }
    }
}

abstract class SearchableComposeController<P : BasePresenter<*>>(bundle: Bundle? = null) :
    SearchableNucleusController<ComposeControllerBinding, P>(bundle),
    ComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            setComposeContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                ComposeContent(nestedScrollInterop)
            }
        }
    }
}

interface FullComposeContentController {
    @Composable fun ComposeContent()
}

interface ComposeContentController {
    @Composable fun ComposeContent(nestedScrollInterop: NestedScrollConnection)
}
