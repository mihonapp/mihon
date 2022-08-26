/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.minimumTouchTargetSize

/**
 * <a href="https://m3.material.io/components/icon-button/overview" class="external" target="_blank">Material Design standard icon button</a>.
 *
 * Icon buttons help people take supplementary actions with a single tap. They’re used when a
 * compact button is required, such as in a toolbar or image list.
 *
 * ![Standard icon button image](https://developer.android.com/images/reference/androidx/compose/material3/standard-icon-button.png)
 *
 * [content] should typically be an [Icon] (see [androidx.compose.material.icons.Icons]). If using a
 * custom icon, note that the typical size for the internal icon is 24 x 24 dp.
 * This icon button has an overall minimum touch target size of 48 x 48dp, to meet accessibility
 * guidelines.
 *
 * @sample androidx.compose.material3.samples.IconButtonSample
 *
 * Tachiyomi changes:
 * * Add on long click
 *
 * @param onClick called when this icon button is clicked
 * @param modifier the [Modifier] to be applied to this icon button
 * @param enabled controls the enabled state of this icon button. When `false`, this component will
 * not respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this icon button. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this icon button in different states.
 * @param colors [IconButtonColors] that will be used to resolve the colors used for this icon
 * button in different states. See [IconButtonDefaults.iconButtonColors].
 * @param content the content of this icon button, typically an [Icon]
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
        modifier
            .minimumTouchTargetSize()
            .size(IconButtonTokens.StateLayerSize)
            .background(color = colors.containerColor(enabled).value)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = rememberRipple(
                    bounded = false,
                    radius = IconButtonTokens.StateLayerSize / 2,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = colors.contentColor(enabled).value
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

object IconButtonDefaults {
    /**
     * Creates a [IconButtonColors] that represents the default colors used in a [IconButton].
     *
     * @param containerColor the container color of this icon button when enabled.
     * @param contentColor the content color of this icon button when enabled.
     * @param disabledContainerColor the container color of this icon button when not enabled.
     * @param disabledContentColor the content color of this icon button when not enabled.
     */
    @Composable
    fun iconButtonColors(
        containerColor: Color = Color.Transparent,
        contentColor: Color = LocalContentColor.current,
        disabledContainerColor: Color = Color.Transparent,
        disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
    ): IconButtonColors =
        IconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
        )
}

object IconButtonTokens {
    val StateLayerSize = 40.0.dp
}

/**
 * Represents the container and content colors used in an icon button in different states.
 *
 * - See [IconButtonDefaults.filledIconButtonColors] and
 * [IconButtonDefaults.filledTonalIconButtonColors] for the default colors used in a
 * [FilledIconButton].
 * - See [IconButtonDefaults.outlinedIconButtonColors] for the default colors used in an
 * [OutlinedIconButton].
 */
@Immutable
class IconButtonColors internal constructor(
    private val containerColor: Color,
    private val contentColor: Color,
    private val disabledContainerColor: Color,
    private val disabledContentColor: Color,
) {
    /**
     * Represents the container color for this icon button, depending on [enabled].
     *
     * @param enabled whether the icon button is enabled
     */
    @Composable
    internal fun containerColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) containerColor else disabledContainerColor)
    }

    /**
     * Represents the content color for this icon button, depending on [enabled].
     *
     * @param enabled whether the icon button is enabled
     */
    @Composable
    internal fun contentColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) contentColor else disabledContentColor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is IconButtonColors) return false

        if (containerColor != other.containerColor) return false
        if (contentColor != other.contentColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (disabledContentColor != other.disabledContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + contentColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + disabledContentColor.hashCode()

        return result
    }
}
