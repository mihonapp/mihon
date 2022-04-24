package eu.kanade.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.composethemeadapter3.createMdc3Theme

@Composable
fun TachiyomiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val (colorScheme, typography) = createMdc3Theme(
        context = context,
        setTextColors = true,
    )

    MaterialTheme(
        colorScheme = colorScheme!!,
        typography = typography!!,
        content = content,
    )
}
