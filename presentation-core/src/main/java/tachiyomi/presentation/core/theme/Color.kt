package tachiyomi.presentation.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ColorScheme.active: Color
    @Composable
    get() {
        return if (isSystemInDarkTheme()) Color(255, 235, 59) else Color(255, 193, 7)
    }

val ColorScheme.positive: Color
    @Composable
    get() {
        return if (isSystemInDarkTheme()) Color(165, 214, 167) else Color(46, 125, 50)
    }

val ColorScheme.onPositive: Color
    @Composable
    get() {
        return if (isSystemInDarkTheme()) Color(10, 56, 22) else Color(255, 255, 255)
    }
