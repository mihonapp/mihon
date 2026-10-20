package eu.kanade.tachiyomi.data.coil

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.util.ExtensionLoader

/**
 * The icon of an installed extension, drawn at [density].
 */
data class InstalledExtensionIcon(
    val pkgName: String,
    val versionCode: Long,
    val density: Int,
)

/**
 * The icon of the installed extension that provides the source with [sourceId].
 */
data class InstalledSourceIcon(val sourceId: Long)

class ExtensionIconFetcher(
    private val context: Context,
    private val getPkgName: suspend () -> String?,
    private val density: Int?,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val pkgName = getPkgName() ?: error("No installed extension")
        val appInfo = ExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)?.applicationInfo
            ?: error("Extension $pkgName isn't installed")
        val drawable: Drawable = if (density == null) {
            appInfo.loadIcon(context.packageManager)
        } else {
            context.packageManager.getResourcesForApplication(appInfo)
                .getDrawableForDensity(appInfo.icon, density, null)
                ?: error("Extension $pkgName has no icon")
        }
        // Adaptive icons aren't bitmaps, and only bitmaps are kept in the memory cache
        return ImageFetchResult(
            image = drawable.toBitmap().asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class ExtensionFactory(private val context: Context) : Fetcher.Factory<InstalledExtensionIcon> {
        override fun create(data: InstalledExtensionIcon, options: Options, imageLoader: ImageLoader): Fetcher {
            return ExtensionIconFetcher(context, { data.pkgName }, data.density)
        }
    }

    class SourceFactory(
        private val context: Context,
        private val extensionManager: Lazy<ExtensionManager>,
    ) : Fetcher.Factory<InstalledSourceIcon> {
        override fun create(data: InstalledSourceIcon, options: Options, imageLoader: ImageLoader): Fetcher {
            return ExtensionIconFetcher(
                context = context,
                getPkgName = { extensionManager.value.getExtensionPackage(data.sourceId) },
                density = null,
            )
        }
    }
}

class InstalledExtensionIconKeyer : Keyer<InstalledExtensionIcon> {
    override fun key(data: InstalledExtensionIcon, options: Options): String {
        return "extension;${data.pkgName};${data.versionCode};${data.density}"
    }
}

class InstalledSourceIconKeyer : Keyer<InstalledSourceIcon> {
    override fun key(data: InstalledSourceIcon, options: Options): String {
        return "source;${data.sourceId}"
    }
}
