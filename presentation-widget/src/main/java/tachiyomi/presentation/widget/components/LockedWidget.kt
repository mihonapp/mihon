package tachiyomi.presentation.widget.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@Composable
fun LockedWidget(
    foreground: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val intent = Intent(context, Class.forName(Constants.MAIN_ACTIVITY)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    Box(
        modifier = modifier
            .clickable(actionStartActivity(intent))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.stringResource(MR.strings.appwidget_unavailable_locked),
            style = TextStyle(
                color = foreground,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
