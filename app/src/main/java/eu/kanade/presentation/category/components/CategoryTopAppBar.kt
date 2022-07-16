package eu.kanade.presentation.category.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

@Composable
fun CategoryTopAppBar(
    navigateUp: () -> Unit,
) {
    SmallTopAppBar(
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.abc_action_bar_up_description),
                )
            }
        },
        title = {
            Text(text = stringResource(R.string.action_edit_categories))
        },
    )
}
