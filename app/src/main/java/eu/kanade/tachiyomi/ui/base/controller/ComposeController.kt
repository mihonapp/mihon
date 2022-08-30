package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.util.view.setComposeContent
import nucleus.presenter.Presenter

abstract class FullComposeController<P : Presenter<*>>(bundle: Bundle? = null) :
    NucleusController<ComposeControllerBinding, P>(bundle),
    ComposeContentController {

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
 * Basic Compose controller without a presenter.
 */
abstract class BasicFullComposeController(bundle: Bundle? = null) :
    BaseController<ComposeControllerBinding>(bundle),
    ComposeContentController {

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

interface ComposeContentController {
    @Composable fun ComposeContent()
}
