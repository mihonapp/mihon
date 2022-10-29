package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.util.horizontalPadding
import eu.kanade.tachiyomi.R

@Composable
fun CategoryListItem(
    modifier: Modifier = Modifier,
    category: Category,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: (Category) -> Unit,
    onMoveDown: (Category) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRename() }
                .padding(start = horizontalPadding, top = horizontalPadding, end = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Outlined.Label, contentDescription = "")
            Text(
                text = category.name,
                modifier = Modifier
                    .padding(start = horizontalPadding),
            )
        }
        Row {
            IconButton(
                onClick = { onMoveUp(category) },
                enabled = canMoveUp,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropUp, contentDescription = "")
            }
            IconButton(
                onClick = { onMoveDown(category) },
                enabled = canMoveDown,
            ) {
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = "")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRename) {
                Icon(imageVector = Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_rename_category))
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
