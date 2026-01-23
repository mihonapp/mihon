package eu.kanade.presentation.browse.components

import android.util.DisplayMetrics
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import eu.kanade.domain.source.model.icon
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.jsplugin.source.JsSource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val defaultModifier = Modifier
    .height(40.dp)
    .aspectRatio(1f)

@Composable
fun SourceIcon(
    source: Source,
    modifier: Modifier = Modifier,
) {
    val icon = source.icon
    
    // Check if actual source is a JsSource
    val actualSource = remember(source.id) {
        Injekt.get<SourceManager>().get(source.id)
    }

    when {
        source.isStub && icon == null -> {
            Image(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = modifier.then(defaultModifier),
            )
        }
        icon != null -> {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        source.isLocal() -> {
            Image(
                painter = painterResource(R.mipmap.ic_local_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
        // Handle JS Source icons
        actualSource is JsSource -> {
            AsyncImage(
                model = actualSource.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.mipmap.ic_default_source),
                modifier = modifier.then(defaultModifier)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        else -> {
            Image(
                painter = painterResource(R.mipmap.ic_default_source),
                contentDescription = null,
                modifier = modifier.then(defaultModifier),
            )
        }
    }
}

@Composable
fun ExtensionIcon(
    extension: Extension,
    modifier: Modifier = Modifier,
    density: Int = DisplayMetrics.DENSITY_DEFAULT,
) {
    when (extension) {
        is Extension.Available -> {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                modifier = modifier
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        is Extension.Installed -> {
            val icon by extension.getIcon(density)
            when (icon) {
                Result.Loading -> Box(modifier = modifier)
                is Result.Success -> Image(
                    bitmap = (icon as Result.Success<ImageBitmap>).value,
                    contentDescription = null,
                    modifier = modifier,
                )
                Result.Error -> Image(
                    bitmap = ImageBitmap.imageResource(id = R.mipmap.ic_default_source),
                    contentDescription = null,
                    modifier = modifier,
                )
            }
        }
        is Extension.Untrusted -> Image(
            imageVector = Icons.Filled.Dangerous,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            modifier = modifier.then(defaultModifier),
        )
        is Extension.JsPlugin -> {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = null,
                placeholder = ColorPainter(Color(0x1F888888)),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                modifier = modifier
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
    }
}

@Composable
private fun Extension.getIcon(density: Int = DisplayMetrics.DENSITY_DEFAULT): State<Result<ImageBitmap>> {
    val context = LocalContext.current
    return produceState<Result<ImageBitmap>>(initialValue = Result.Loading, this) {
        withIOContext {
            value = try {
                val appInfo = ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo!!
                val appResources = context.packageManager.getResourcesForApplication(appInfo)
                Result.Success(
                    appResources.getDrawableForDensity(appInfo.icon, density, null)!!
                        .toBitmap()
                        .asImageBitmap(),
                )
            } catch (e: Exception) {
                Result.Error
            }
        }
    }
}

sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data object Error : Result<Nothing>()
    data class Success<out T>(val value: T) : Result<T>()
}
