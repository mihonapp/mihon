package mihon.desktop.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.ui.common.MangaCover
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Composable
fun MangaCoverDialog(
    mangaTitle: String,
    thumbnailUrl: String?,
    mangaId: Long,
    isCustomCover: Boolean,
    onDismissRequest: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
    coverFileProvider: () -> Path? = { null },
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var saveStatusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .testTag("manga-cover-dialog-scrim"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .testTag("manga-cover-dialog-card"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mangaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp).testTag("manga-cover-dialog-close"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.dialogClose,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    MangaCover(
                        thumbnailUrl = thumbnailUrl,
                        mangaId = mangaId,
                        contentDescription = mangaTitle,
                        modifier = Modifier
                            .width(280.dp)
                            .height(400.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag("manga-cover-dialog-image"),
                        shape = RoundedCornerShape(12.dp),
                    )

                    saveStatusMessage?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("manga-cover-save-status"),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val sourcePath = coverFileProvider()
                                    if (sourcePath != null && Files.isRegularFile(sourcePath)) {
                                        val picturesDir = System.getProperty("user.home")
                                            ?.let { File(it, "Pictures") }
                                            ?.takeIf { it.exists() }
                                            ?: File(System.getProperty("user.home"), "Downloads")

                                        val safeName = mangaTitle
                                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                            .take(40)
                                        val ext = sourcePath.fileName.toString().substringAfterLast('.', "jpg")
                                        val destFile = File(picturesDir, "${safeName}_cover.$ext")

                                        try {
                                            Files.copy(
                                                sourcePath,
                                                destFile.toPath(),
                                                StandardCopyOption.REPLACE_EXISTING,
                                            )
                                            withContext(Dispatchers.Main) {
                                                saveStatusMessage = "${strings.mangaDetailCoverSaved}: ${destFile.name}"
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                saveStatusMessage = strings.text(UiText.SaveFailed, e.message.orEmpty())
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            saveStatusMessage = strings.mangaDetailCoverSaved
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.testTag("manga-cover-dialog-save-btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.mangaDetailCoverSave)
                        }

                        OutlinedButton(
                            onClick = {
                                onChangeCover()
                                onDismissRequest()
                            },
                            modifier = Modifier.testTag("manga-cover-dialog-change-btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.mangaDetailChangeCover)
                        }

                        if (isCustomCover) {
                            OutlinedButton(
                                onClick = {
                                    onResetCover()
                                    onDismissRequest()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.testTag("manga-cover-dialog-reset-btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.mangaDetailResetCover)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaCoverDialog(
    manga: LibraryManga,
    isCustomCover: Boolean,
    onDismissRequest: () -> Unit,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
    coverFileProvider: () -> Path? = { null },
    modifier: Modifier = Modifier,
) = MangaCoverDialog(
    mangaTitle = manga.title,
    thumbnailUrl = manga.thumbnailUrl,
    mangaId = manga.id,
    isCustomCover = isCustomCover,
    onDismissRequest = onDismissRequest,
    onChangeCover = onChangeCover,
    onResetCover = onResetCover,
    coverFileProvider = coverFileProvider,
    modifier = modifier,
)
