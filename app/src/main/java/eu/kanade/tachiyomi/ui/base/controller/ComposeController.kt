package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.OnBackPressedDispatcherOwner
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

    override fun handleBack(): Boolean {
        val dispatcher = (activity as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher ?: return false
        return if (dispatcher.hasEnabledCallbacks()) {
            dispatcher.onBackPressed()
            true
        } else {
            false
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

    // Let Compose view handle this
    override fun handleBack(): Boolean {
        val dispatcher = (activity as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher ?: return false
        return if (dispatcher.hasEnabledCallbacks()) {
            dispatcher.onBackPressed()
            true
        } else {
            false
        }
    }
}

interface ComposeContentController {
    @Composable fun ComposeContent()
}
