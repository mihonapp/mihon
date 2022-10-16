package eu.kanade.presentation.more.settings.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.LocalPreferenceHighlighted
import eu.kanade.presentation.util.secondaryItemAlpha
import kotlinx.coroutines.delay

@Composable
internal fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    modifier = Modifier
                        .padding(
                            start = HorizontalPadding,
                            top = 0.dp,
                            end = HorizontalPadding,
                        )
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                )
            }
        } else {
            null
        },
        icon = icon,
        onClick = onClick,
        widget = widget,
    )
}

@Composable
internal fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    BasePreferenceWidgetImpl(modifier, title, subcomponent, icon, onClick, widget)
}

@Composable
private fun BasePreferenceWidgetImpl(
    modifier: Modifier = Modifier,
    title: String,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    val highlighted = LocalPreferenceHighlighted.current
    Box(modifier = Modifier.highlightBackground(highlighted)) {
        Row(
            modifier = modifier
                .sizeIn(minHeight = 56.dp)
                .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = HorizontalPadding, end = 0.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp),
            ) {
                if (title.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(horizontal = HorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp,
                        )
                    }
                }
                subcomponent?.invoke(this)
            }
            if (widget != null) {
                Box(modifier = Modifier.padding(end = HorizontalPadding)) {
                    widget()
                }
            }
        }
    }
}

internal fun Modifier.highlightBackground(highlighted: Boolean): Modifier = composed {
    var highlightFlag by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (highlighted) {
            highlightFlag = true
            delay(3000)
            highlightFlag = false
        }
    }
    val highlight by animateColorAsState(
        targetValue = if (highlightFlag) {
            MaterialTheme.colorScheme.surfaceTint.copy(alpha = .12f)
        } else {
            Color.Transparent
        },
        animationSpec = if (highlightFlag) {
            repeatable(
                iterations = 5,
                animation = tween(durationMillis = 200),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(
                    offsetMillis = 600,
                    offsetType = StartOffsetType.Delay,
                ),
            )
        } else {
            tween(200)
        },
    )
    then(Modifier.background(color = highlight))
}

internal val TrailingWidgetBuffer = 16.dp
internal val HorizontalPadding = 24.dp
