package eu.kanade.presentation.theme

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.google.android.material.composethemeadapter3.createMdc3Theme
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    val (colorScheme, typography) = createMdc3Theme(
        context = context,
        layoutDirection = layoutDirection,
    )

    MaterialTheme(
        colorScheme = colorScheme!!,
        typography = typography!!,
        content = content,
    )
}

@Composable
fun TachiyomiTheme(
    appTheme: AppTheme,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val originalContext = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val themedContext = remember(appTheme, originalContext) {
        val themeResIds = ThemingDelegate.getThemeResIds(appTheme, amoled)
        themeResIds.fold(originalContext) { context, themeResId ->
            ContextThemeWrapper(context, themeResId)
        }
    }
    val (colorScheme, typography) = createMdc3Theme(
        context = themedContext,
        layoutDirection = layoutDirection,
    )

    MaterialTheme(
        colorScheme = colorScheme!!,
        typography = typography!!,
        content = content,
    )
}
