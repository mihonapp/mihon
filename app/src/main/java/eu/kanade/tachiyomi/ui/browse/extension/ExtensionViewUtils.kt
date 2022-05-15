package eu.kanade.tachiyomi.ui.browse.extension

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.lang.withIOContext

fun Extension.getApplicationIcon(context: Context): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(pkgName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

@Composable
fun Extension.getIcon(): State<Result<ImageBitmap>> {
    val context = LocalContext.current
    return produceState<Result<ImageBitmap>>(initialValue = Result.Loading, this) {
        withIOContext {
            value = try {
                Result.Success(
                    context.packageManager.getApplicationIcon(pkgName)
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
    object Loading : Result<Nothing>()
    object Error : Result<Nothing>()
    data class Success<out T>(val value: T) : Result<T>()
}
