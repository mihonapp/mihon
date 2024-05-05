package eu.kanade.presentation.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.more.settings.LocalPreferenceMinHeight
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DuplicateMangaDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: () -> Unit,
    onMigrate: () -> Unit,
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
                text = stringResource(MR.strings.are_you_sure),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(MR.strings.confirm_add_duplicate_manga),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(PaddingSize))

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_show_manga),
                icon = Icons.Outlined.Book,
                onPreferenceClick = {
                    onDismissRequest()
                    onOpenManga()
                },
            )

            HorizontalDivider()

            TextPreferenceWidget(
                title = stringResource(MR.strings.action_migrate_duplicate),
                icon = Icons.Outlined.SwapVert,
                onPreferenceClick = {
                    onDismissRequest()
                    onMigrate()
                },
            )

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

private val PaddingSize = 16.dp

private val ButtonPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp, top = 8.dp)
