package tachiyomi.presentation.widget.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount

@Composable
fun UpdatesWidget(
    data: ImmutableList<Pair<Long, Bitmap?>>?,
    contentColor: ColorProvider,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        if (data == null) {
            CircularProgressIndicator(color = contentColor)
        } else if (data.isEmpty()) {
            Text(
                text = stringResource(MR.strings.information_no_recent),
                style = TextStyle(color = contentColor),
            )
        } else {
            val (rowCount, columnCount) = LocalSize.current.calculateRowAndColumnCount(topPadding, bottomPadding)
            Column(
                modifier = GlanceModifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                (0..<rowCount).forEach { i ->
                    val coverRow = (0..<columnCount).mapNotNull { j ->
                        data.getOrNull(j + (i * columnCount))
                    }
                    if (coverRow.isNotEmpty()) {
                        Row(
                            modifier = GlanceModifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            coverRow.forEach { (mangaId, cover) ->
                                Box(
                                    modifier = GlanceModifier
                                        .padding(horizontal = 3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val intent = Intent(
                                        LocalContext.current,
                                        Class.forName(Constants.MAIN_ACTIVITY),
                                    ).apply {
                                        action = Constants.SHORTCUT_MANGA
                                        putExtra(Constants.MANGA_EXTRA, mangaId)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                                        // https://issuetracker.google.com/issues/238793260
                                        addCategory(mangaId.toString())
                                    }
                                    UpdatesMangaCover(
                                        cover = cover,
                                        modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
