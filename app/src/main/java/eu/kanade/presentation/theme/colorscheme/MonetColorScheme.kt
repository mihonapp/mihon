package eu.kanade.presentation.theme.colorscheme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme

internal class MonetColorScheme(context: Context) : BaseColorScheme() {

    private val monet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MonetSystemColorScheme(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val seed = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
        if (seed != null) {
            MonetCompatColorScheme(Color(seed))
        } else {
            TachiyomiColorScheme
        }
    } else {
        TachiyomiColorScheme
    }

    override val darkScheme
        get() = monet.darkScheme

    override val lightScheme
        get() = monet.lightScheme
}

@RequiresApi(Build.VERSION_CODES.S)
private class MonetSystemColorScheme(context: Context) : BaseColorScheme() {
    override val lightScheme = dynamicLightColorScheme(context)
    override val darkScheme = dynamicDarkColorScheme(context)
}

internal class MonetCompatColorScheme(seed: Color) : BaseColorScheme() {
    override val lightScheme = generateColorSchemeFromSeed(seed = seed, dark = false)
    override val darkScheme = generateColorSchemeFromSeed(seed = seed, dark = true)

    companion object {
        fun generateColorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
            return DynamicScheme(seedColor = seed, isDark = dark)
                .toColorScheme(isAmoled = false)
        }
    }
}
