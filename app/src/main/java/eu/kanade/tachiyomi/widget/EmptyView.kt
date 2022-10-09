package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.StringRes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.core.view.isVisible
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.theme.TachiyomiTheme

class EmptyView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    var message by mutableStateOf("")

    /**
     * Hide the information view
     */
    fun hide() {
        this.isVisible = false
    }

    /**
     * Show the information view
     * @param textResource text of information view
     */
    fun show(@StringRes textResource: Int) {
        message = context.getString(textResource)
        this.isVisible = true
    }

    @Composable
    override fun Content() {
        TachiyomiTheme {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                EmptyScreen(message = message)
            }
        }
    }
}
