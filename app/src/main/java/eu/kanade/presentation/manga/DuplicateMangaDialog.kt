package eu.kanade.presentation.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun DuplicateMangaDialog(
    onDismissRequest: () -> Unit,
    duplicateManga: List<Manga>,
    onConfirm: () -> Unit,
    onOpenManga: (manga: Manga) -> Unit,
    onMigrate: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minHeight = LocalPreferenceMinHeight.current

    AdaptiveSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    vertical = TabbedDialogPaddings.Vertical,
                    horizontal = TabbedDialogPaddings.Horizontal,
                )
                .fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(TitlePadding),
                text = stringResource(MR.strings.possible_duplicate),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(MR.strings.duplicate_manga_select),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(PaddingSize))

            LazyRow (
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(
                    items = duplicateManga,
                    key = { it },
                ) {
                    DuplicateMangaListItem(
                        manga = it,
                        onMigrate = { onMigrate(it) },
                        onDismissRequest = onDismissRequest,
                        onOpenManga = { onOpenManga(it) },
                    )
                }
            }

            Spacer(Modifier.height(PaddingSize))

            HorizontalDivider()

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_add_anyway),
                icon = Icons.Outlined.Add,
                onPreferenceClick = {
                    onDismissRequest()
                    onConfirm()
                },
            )

            Row(
                modifier = Modifier
                    .sizeIn(minHeight = minHeight)
                    .clickable { onDismissRequest.invoke() }
                    .padding(ButtonPadding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier
                            .padding(vertical = 8.dp),
                        text = stringResource(MR.strings.action_cancel),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateMangaListItem(
    manga: Manga,
    onDismissRequest: () -> Unit,
    onOpenManga: () -> Unit,
    onMigrate: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
            .combinedClickable(
                onLongClick = { onOpenManga() },
                onClick = {
                    onDismissRequest()
                    onMigrate()
                },
            ),
    ) {
        Column {
            Row {
                MangaCover.Book(
                    data = ImageRequest.Builder(LocalContext.current)
                        .data(manga)
                        .crossfade(true)
                        .build(),
                    modifier = Modifier.height(96.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    if (!manga.author.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.secondaryItemAlpha(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = manga.author!!,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }

                    if (!manga.artist.isNullOrBlank() && manga.author != manga.artist) {
                        Row(
                            modifier = Modifier.secondaryItemAlpha(),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = manga.artist!!,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.secondaryItemAlpha(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when (manga.status) {
                                SManga.ONGOING.toLong() -> Icons.Outlined.Schedule
                                SManga.COMPLETED.toLong() -> Icons.Outlined.DoneAll
                                SManga.LICENSED.toLong() -> Icons.Outlined.AttachMoney
                                SManga.PUBLISHING_FINISHED.toLong() -> Icons.Outlined.Done
                                SManga.CANCELLED.toLong() -> Icons.Outlined.Close
                                SManga.ON_HIATUS.toLong() -> Icons.Outlined.Pause
                                else -> Icons.Outlined.Block
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(16.dp),
                        )
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Text(
                                text = when (manga.status) {
                                    SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
                                    SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
                                    SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
                                    SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
                                    SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
                                    SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
                                    else -> stringResource(MR.strings.unknown)
                                },
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PaddingSize = 16.dp

private val ButtonPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp, top = 8.dp)
