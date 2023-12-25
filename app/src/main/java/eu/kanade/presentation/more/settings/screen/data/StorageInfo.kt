package eu.kanade.presentation.more.settings.screen.data

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

@Composable
fun StorageInfo(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val storages = remember { DiskUtil.getExternalStorages(context) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        storages.forEach {
            StorageInfo(it)
        }
    }
}

@Composable
private fun StorageInfo(
    file: File,
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    val available = remember(file) { DiskUtil.getAvailableStorageSpace(file) }
    val availableText = remember(available) { Formatter.formatFileSize(context, available) }
    val total = remember(file) { DiskUtil.getTotalStorageSpace(file) }
    val totalText = remember(total) { Formatter.formatFileSize(context, total) }

    val cornerRadius = CornerRadius(100f, 100f)
    val usedBarColor = MaterialTheme.colorScheme.primary
    val totalBarColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = file.absolutePath,
            fontWeight = FontWeight.Medium,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        ) {
            drawRoundRect(
                color = totalBarColor,
                cornerRadius = cornerRadius,
            )

            drawPath(
                path = Path().apply {
                    val pathSize = Size(
                        width = (1 - (available / total.toFloat())) * size.width,
                        height = size.height,
                    )
                    addRoundRect(
                        if (layoutDirection == LayoutDirection.Ltr) {
                            RoundRect(
                                rect = Rect(
                                    offset = Offset(0f, 0f),
                                    size = pathSize,
                                ),
                                topLeft = cornerRadius,
                                bottomLeft = cornerRadius,
                            )
                        } else {
                            RoundRect(
                                rect = Rect(
                                    offset = Offset(size.width - pathSize.width, 0f),
                                    size = pathSize,
                                ),
                                topRight = cornerRadius,
                                bottomRight = cornerRadius,
                            )
                        },
                    )
                },
                color = usedBarColor,
            )
        }

        Text(
            text = stringResource(MR.strings.available_disk_space_info, availableText, totalText),
        )
    }
}
