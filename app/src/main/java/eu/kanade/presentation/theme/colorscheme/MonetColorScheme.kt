package eu.kanade.presentation.theme.colorscheme

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.getSystemService
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.Score

internal class MonetColorScheme(context: Context) : BaseColorScheme() {

    private val monet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MonetSystemColorScheme(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val seed = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
        if (seed != null) {
            MonetCompatColorScheme(context, seed)
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

    companion object {
        @Suppress("Unused")
        @SuppressLint("RestrictedApi")
        fun extractSeedColorFromImage(bitmap: Bitmap): Int? {
            val width = bitmap.width
            val height = bitmap.height
            val bitmapPixels = IntArray(width * height)
            bitmap.getPixels(bitmapPixels, 0, width, 0, 0, width, height)
            return Score.score(QuantizerCelebi.quantize(bitmapPixels, 128), 1, 0)[0]
                .takeIf { it != 0 } // Don't take fallback color
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class MonetSystemColorScheme(context: Context) : BaseColorScheme() {
    override val lightScheme = dynamicLightColorScheme(context)
    override val darkScheme = dynamicDarkColorScheme(context)
}

private class MonetCompatColorScheme(context: Context, seed: Int) : BaseColorScheme() {

    override val lightScheme = generateColorSchemeFromSeed(context = context, seed = seed, dark = false)
    override val darkScheme = generateColorSchemeFromSeed(context = context, seed = seed, dark = true)

    companion object {
        private fun Int.toComposeColor(): Color = Color(this)

        @SuppressLint("PrivateResource", "RestrictedApi")
        private fun generateColorSchemeFromSeed(context: Context, seed: Int, dark: Boolean): ColorScheme {
            val scheme = SchemeContent(
                Hct.fromInt(seed),
                dark,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    context.getSystemService<UiModeManager>()?.contrast?.toDouble() ?: 0.0
                } else {
                    0.0
                },
            )
            val dynamicColors = MaterialDynamicColors()
            return ColorScheme(
                primary = dynamicColors.primary().getArgb(scheme).toComposeColor(),
                onPrimary = dynamicColors.onPrimary().getArgb(scheme).toComposeColor(),
                primaryContainer = dynamicColors.primaryContainer().getArgb(scheme).toComposeColor(),
                onPrimaryContainer = dynamicColors.onPrimaryContainer().getArgb(scheme).toComposeColor(),
                inversePrimary = dynamicColors.inversePrimary().getArgb(scheme).toComposeColor(),
                secondary = dynamicColors.secondary().getArgb(scheme).toComposeColor(),
                onSecondary = dynamicColors.onSecondary().getArgb(scheme).toComposeColor(),
                secondaryContainer = dynamicColors.secondaryContainer().getArgb(scheme).toComposeColor(),
                onSecondaryContainer = dynamicColors.onSecondaryContainer().getArgb(scheme).toComposeColor(),
                tertiary = dynamicColors.tertiary().getArgb(scheme).toComposeColor(),
                onTertiary = dynamicColors.onTertiary().getArgb(scheme).toComposeColor(),
                tertiaryContainer = dynamicColors.tertiary().getArgb(scheme).toComposeColor(),
                onTertiaryContainer = dynamicColors.onTertiaryContainer().getArgb(scheme).toComposeColor(),
                background = dynamicColors.background().getArgb(scheme).toComposeColor(),
                onBackground = dynamicColors.onBackground().getArgb(scheme).toComposeColor(),
                surface = dynamicColors.surface().getArgb(scheme).toComposeColor(),
                onSurface = dynamicColors.onSurface().getArgb(scheme).toComposeColor(),
                surfaceVariant = dynamicColors.surfaceVariant().getArgb(scheme).toComposeColor(),
                onSurfaceVariant = dynamicColors.onSurfaceVariant().getArgb(scheme).toComposeColor(),
                surfaceTint = dynamicColors.surfaceTint().getArgb(scheme).toComposeColor(),
                inverseSurface = dynamicColors.inverseSurface().getArgb(scheme).toComposeColor(),
                inverseOnSurface = dynamicColors.inverseOnSurface().getArgb(scheme).toComposeColor(),
                error = dynamicColors.error().getArgb(scheme).toComposeColor(),
                onError = dynamicColors.onError().getArgb(scheme).toComposeColor(),
                errorContainer = dynamicColors.errorContainer().getArgb(scheme).toComposeColor(),
                onErrorContainer = dynamicColors.onErrorContainer().getArgb(scheme).toComposeColor(),
                outline = dynamicColors.outline().getArgb(scheme).toComposeColor(),
                outlineVariant = dynamicColors.outlineVariant().getArgb(scheme).toComposeColor(),
                scrim = Color.Black,
                surfaceBright = dynamicColors.surfaceBright().getArgb(scheme).toComposeColor(),
                surfaceDim = dynamicColors.surfaceDim().getArgb(scheme).toComposeColor(),
                surfaceContainer = dynamicColors.surfaceContainer().getArgb(scheme).toComposeColor(),
                surfaceContainerHigh = dynamicColors.surfaceContainerHigh().getArgb(scheme).toComposeColor(),
                surfaceContainerHighest = dynamicColors.surfaceContainerHighest().getArgb(scheme).toComposeColor(),
                surfaceContainerLow = dynamicColors.surfaceContainerLow().getArgb(scheme).toComposeColor(),
                surfaceContainerLowest = dynamicColors.surfaceContainerLowest().getArgb(scheme).toComposeColor(),
            )
        }
    }
}
