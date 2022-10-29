package eu.kanade.presentation.more.settings.widget

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.DIVIDER_ALPHA
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.secondaryItemAlpha
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable

@Composable
internal fun AppThemePreferenceWidget(
    title: String,
    value: AppTheme,
    amoled: Boolean,
    onItemClick: (AppTheme) -> Unit,
) {
    BasePreferenceWidget(
        title = title,
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
    val appThemes = remember {
        AppTheme.values()
            .filterNot { it.titleResId == null || (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable) }
    }
    LazyRow(
        modifier = Modifier
            .animateContentSize()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        onClick = { onItemClick(appTheme) },
                    )
                }

                Text(
                    text = stringResource(appTheme.titleResId!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .secondaryItemAlpha(),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .border(
                width = 4.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    dividerColor
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
                        contentDescription = stringResource(R.string.selected),
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
                    color = dividerColor,
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

@Preview(
    name = "light",
    showBackground = true,
)
@Preview(
    name = "dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    TachiyomiTheme {
        AppThemesList(
            currentTheme = appTheme,
            amoled = false,
            onItemClick = { appTheme = it },
        )
    }
}
