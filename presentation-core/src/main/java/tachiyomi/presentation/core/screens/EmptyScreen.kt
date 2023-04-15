package tachiyomi.presentation.core.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.secondaryItemAlpha
import kotlin.random.Random

data class EmptyScreenAction(
    @StringRes val stringResId: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun EmptyScreen(
    @StringRes textResource: Int,
    modifier: Modifier = Modifier,
    actions: List<EmptyScreenAction>? = null,
) {
    EmptyScreen(
        message = stringResource(textResource),
        modifier = modifier,
        actions = actions,
    )
}

@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier,
    actions: List<EmptyScreenAction>? = null,
) {
    val face = remember { getRandomErrorFace() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = face,
            modifier = Modifier.secondaryItemAlpha(),
            style = MaterialTheme.typography.displayMedium,
        )

        Text(
            text = message,
            modifier = Modifier
                .paddingFromBaseline(top = 24.dp)
                .secondaryItemAlpha(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        if (!actions.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                actions.fastForEach {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(it.stringResId),
                        icon = it.icon,
                        onClick = it.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val ERROR_FACES = listOf(
    "(･o･;)",
    "Σ(ಠ_ಠ)",
    "ಥ_ಥ",
    "(˘･_･˘)",
    "(；￣Д￣)",
    "(･Д･。",
)

private fun getRandomErrorFace(): String {
    return ERROR_FACES[Random.nextInt(ERROR_FACES.size)]
}
