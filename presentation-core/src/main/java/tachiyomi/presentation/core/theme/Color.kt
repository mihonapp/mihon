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
