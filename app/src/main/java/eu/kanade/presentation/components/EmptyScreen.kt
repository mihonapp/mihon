package eu.kanade.presentation.components

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.secondaryItemAlpha
import eu.kanade.tachiyomi.R
import kotlin.random.Random

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
    Layout(
        content = {
            Column(
                modifier = Modifier
                    .layoutId("face")
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = face,
                    modifier = Modifier.secondaryItemAlpha(),
                    style = MaterialTheme.typography.displayMedium,
                )

                Text(
                    text = message,
                    modifier = Modifier.paddingFromBaseline(top = 24.dp).secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            if (!actions.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .layoutId("actions")
                        .padding(
                            top = 24.dp,
                            start = horizontalPadding,
                            end = horizontalPadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    actions.forEach {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            title = stringResource(it.stringResId),
                            icon = it.icon,
                            onClick = it.onClick,
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val facePlaceable = measurables.first { it.layoutId == "face" }
            .measure(looseConstraints)
        val actionsPlaceable = measurables.firstOrNull { it.layoutId == "actions" }
            ?.measure(looseConstraints)

        layout(constraints.maxWidth, constraints.maxHeight) {
            val faceY = (constraints.maxHeight - facePlaceable.height) / 2
            facePlaceable.placeRelative(
                x = (constraints.maxWidth - facePlaceable.width) / 2,
                y = faceY,
            )

            actionsPlaceable?.placeRelative(
                x = (constraints.maxWidth - actionsPlaceable.width) / 2,
                y = faceY + facePlaceable.height,
            )
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

@Preview(
    name = "Light",
    widthDp = 400,
    heightDp = 400,
)
@Preview(
    name = "Dark",
    widthDp = 400,
    heightDp = 400,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun NoActionPreview() {
    TachiyomiTheme {
        Surface {
            EmptyScreen(
                textResource = R.string.empty_screen,
            )
        }
    }
}

@Preview(
    name = "Light",
    widthDp = 400,
    heightDp = 400,
)
@Preview(
    name = "Dark",
    widthDp = 400,
    heightDp = 400,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun WithActionPreview() {
    TachiyomiTheme {
        Surface {
            EmptyScreen(
                textResource = R.string.empty_screen,
                actions = listOf(
                    EmptyScreenAction(
                        stringResId = R.string.action_retry,
                        icon = Icons.Default.Refresh,
                        onClick = {},
                    ),
                    EmptyScreenAction(
                        stringResId = R.string.getting_started_guide,
                        icon = Icons.Default.HelpOutline,
                        onClick = {},
                    ),
                ),
            )
        }
    }
}

data class EmptyScreenAction(
    @StringRes val stringResId: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private val horizontalPadding = 24.dp

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
