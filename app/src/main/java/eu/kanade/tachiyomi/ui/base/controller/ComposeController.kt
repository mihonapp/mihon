package eu.kanade.tachiyomi.ui.base.controller

import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.databinding.ComposeControllerBinding
import nucleus.presenter.Presenter

abstract class ComposeController<P : Presenter<*>> : NucleusController<ComposeControllerBinding, P>() {

    override fun createBinding(inflater: LayoutInflater): ComposeControllerBinding =
        ComposeControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        binding.root.setContent {
            TachiyomiTheme {
                ComposeContent()
            }
        }
    }

    @Composable abstract fun ComposeContent()
}
