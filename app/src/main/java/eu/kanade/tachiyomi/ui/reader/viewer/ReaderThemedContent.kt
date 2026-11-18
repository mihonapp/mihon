package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.theme.TachiyomiTheme

/**
 * [content] in the app's theme under the reader's own night mode, from [readerThemedContext].
 *
 * Pages sit on the reader background, so a light app over a black reader would otherwise draw dark text on black.
 * [TachiyomiTheme] reads that from the configuration, which the view-based viewers got by inflating their views
 * from a reader-themed context.
 */
@Composable
fun ReaderThemedContent(readerThemedContext: Context, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContext provides readerThemedContext,
        LocalConfiguration provides readerThemedContext.resources.configuration,
    ) {
        TachiyomiTheme(content = content)
    }
}
