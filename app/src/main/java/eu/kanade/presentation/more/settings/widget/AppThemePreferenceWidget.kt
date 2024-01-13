package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

@Composable
internal fun AppThemePreferenceWidget(
    value: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
) {
    BasePreferenceWidget(
        subcomponent = {
            AppThemesList(
                currentTheme = value,
                amoled = amoled,
                onItemClick = onItemClick,
            )
        },
    )
}

@Composable
private fun AppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
) {
    val context = LocalContext.current
    val appThemes = remember {
        AppTheme.entries
            .filterNot { it.titleRes == null || (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = appThemes,
            key = { it.name },
        ) { appTheme ->
            Column(
                modifier = Modifier
                    .width(114.dp)
                    .padding(top = 8.dp),
            ) {
                TachiyomiTheme(
                    appTheme = appTheme,
                    amoled = amoled,
                ) {
                    AppThemePreviewItem(
                        selected = currentTheme == appTheme,
                        onClick = {
                            onItemClick(appTheme)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(appTheme.titleRes!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .secondaryItemAlpha(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun AppThemePreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .border(
                width = 4.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    DividerDefaults.color
                },
                shape = RoundedCornerShape(17.dp),
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .weight(0.7f)
                    .padding(end = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small,
                    ),
            )

            Box(
                modifier = Modifier.weight(0.3f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.selected),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Cover
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 2.dp)
                .background(
                    color = DividerDefaults.color,
                    shape = MaterialTheme.shapes.small,
                )
                .fillMaxWidth(0.5f)
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }

        // Bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
    TachiyomiTheme(appTheme = appTheme) {
        Surface {
            AppThemesList(
                currentTheme = appTheme,
                amoled = false,
                onItemClick = { appTheme = it },
            )
        }
    }
}
