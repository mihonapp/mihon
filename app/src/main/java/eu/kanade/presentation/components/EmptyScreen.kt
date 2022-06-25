package eu.kanade.presentation.components

import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.widget.EmptyView

@Composable
fun EmptyScreen(
    @StringRes textResource: Int,
    actions: List<EmptyView.Action>? = null,
) {
    EmptyScreen(
        message = stringResource(textResource),
        actions = actions,
    )
}

@Composable
fun EmptyScreen(
    message: String,
    actions: List<EmptyView.Action>? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        AndroidView(
            factory = { context ->
                EmptyView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.Center),
        ) { view ->
            view.show(message, actions)
        }
    }
}
