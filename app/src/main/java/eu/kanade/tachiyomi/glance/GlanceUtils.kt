package eu.kanade.tachiyomi.glance

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import eu.kanade.tachiyomi.R

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_background_radius)
}

fun GlanceModifier.appWidgetInnerRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_inner_radius)
}

@Composable
fun stringResource(@StringRes id: Int): String {
    return LocalContext.current.getString(id)
}
