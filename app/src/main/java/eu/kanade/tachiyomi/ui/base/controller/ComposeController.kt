package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import nucleus.presenter.Presenter

abstract class FullComposeController<P : Presenter<*>>(bundle: Bundle? = null) :
    NucleusController<ComposeControllerBinding, P>(bundle),
    FullComposeContentController {

    override fun createBinding(inflater: LayoutInflater) =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.apply {
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TachiyomiTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        ComposeContent()
                    }
                }
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
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                TachiyomiTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        ComposeContent(nestedScrollInterop)
                    }
                }
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
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                TachiyomiTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        ComposeContent(nestedScrollInterop)
                    }
                }
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
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val nestedScrollInterop = rememberNestedScrollInteropConnection()
                TachiyomiTheme {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        ComposeContent(nestedScrollInterop)
                    }
                }
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
