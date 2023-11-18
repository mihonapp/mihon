package tachiyomi.presentation.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.i18n.localize
import tachiyomi.core.i18n.localizePlural

@Composable
@ReadOnlyComposable
fun localize(resource: StringResource): String {
    return LocalContext.current.localize(resource)
}

@Composable
@ReadOnlyComposable
fun localize(resource: StringResource, vararg args: Any): String {
    return LocalContext.current.localize(resource, *args)
}

@Composable
@ReadOnlyComposable
fun localizePlural(resource: PluralsResource, count: Int): String {
    return LocalContext.current.localizePlural(resource, count)
}

@Composable
@ReadOnlyComposable
fun localizePlural(resource: PluralsResource, count: Int, vararg args: Any): String {
    return LocalContext.current.localizePlural(resource, count, *args)
}
