package eu.kanade.tachiyomi.ui.browse.source.blockrule.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BlockruleListItem(
    blockrule: Blockrule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: (Blockrule) -> Unit,
    onMoveDown: (Blockrule) -> Unit,
    onEdit: () -> Unit,
    enable: Boolean,
    onEnable: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                top = MaterialTheme.padding.small,
                end = MaterialTheme.padding.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = enable,
                onClick = { onEnable(!enable) },
            )
            Text(
                fontWeight = FontWeight.Bold,
                text = blockrule.name,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onMoveUp(blockrule) },
                enabled = canMoveUp,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropUp,
                    contentDescription = null,
                )
            }
            IconButton(
                onClick = { onMoveDown(blockrule) },
                enabled = canMoveDown,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_edit),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
        Row(
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.medium,
            ),
        ) {
            Text(text = blockrule.type.toShowName())
            Text(
                text = blockrule.rule,
                modifier = Modifier.padding(start = MaterialTheme.padding.medium),
            )
        }
    }
}
