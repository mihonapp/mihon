package eu.kanade.presentation.more.settings.database.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.tachiyomi.R

@Composable
fun ClearDatabaseFloatingActionButton(
    isVisible: Boolean,
    onClickDelete: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ExtendedFloatingActionButton(
            modifier = Modifier.navigationBarsPadding(),
            text = {
                Text(text = stringResource(id = R.string.action_delete))
            },
            icon = {
                Icon(Icons.Outlined.Delete, contentDescription = "")
            },
            onClick = onClickDelete,
        )
    }
}
