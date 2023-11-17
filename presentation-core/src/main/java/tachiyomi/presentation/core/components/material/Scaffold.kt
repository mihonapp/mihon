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

@file:Suppress("KDocUnresolvedReference")

package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import kotlin.math.max

/**
 * <a href="https://material.io/design/layout/understanding-layout.html" class="external" target="_blank">Material Design layout</a>.
 *
 * Scaffold implements the basic material design visual layout structure.
 *
 * This component provides API to put together several material components to construct your
 * screen, by ensuring proper layout strategy for them and collecting necessary data so these
 * components will work together correctly.
 *
 * Simple example of a Scaffold with [SmallTopAppBar], [FloatingActionButton]:
 *
 * @sample androidx.compose.material3.samples.SimpleScaffoldWithTopBar
 *
 * To show a [Snackbar], use [SnackbarHostState.showSnackbar].
 *
 * @sample androidx.compose.material3.samples.ScaffoldWithSimpleSnackbar
 *
 * Tachiyomi changes:
 * * Pass scroll behavior to top bar by default
 * * Remove height constraint for expanded app bar
 * * Also take account of fab height when providing inner padding
 * * Add startBar slot for Navigation Rail
 *
 * @param modifier the [Modifier] to be applied to this scaffold
 * @param topBar top app bar of the screen, typically a [SmallTopAppBar]
 * @param startBar side bar on the start of the screen, typically a [NavigationRail]
 * @param bottomBar bottom bar of the screen, typically a [NavigationBar]
 * @param snackbarHost component to host [Snackbar]s that are pushed to be shown via
 * [SnackbarHostState.showSnackbar], typically a [SnackbarHost]
 * @param floatingActionButton Main action button of the screen, typically a [FloatingActionButton]
 * @param floatingActionButtonPosition position of the FAB on the screen. See [FabPosition].
 * @param containerColor the color used for the background of this scaffold. Use [Color.Transparent]
 * to have no color.
 * @param contentColor the preferred color for content inside this scaffold. Defaults to either the
 * matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param contentWindowInsets window insets to be passed to content slot via PaddingValues params.
 * Scaffold will take the insets into account from the top/bottom only if the topBar/ bottomBar
 * are not present, as the scaffold expect topBar/bottomBar to handle insets instead
 * @param content content of the screen. The lambda receives a [PaddingValues] that should be
 * applied to the content root via [Modifier.padding] and [Modifier.consumeWindowInsets] to
 * properly offset top and bottom bars. If using [Modifier.verticalScroll], apply this modifier to
 * the child of the scroll, and not on the scroll itself.
 */
@ExperimentalMaterial3Api
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    startBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
            .then(modifier),
        color = containerColor,
        contentColor = contentColor,
    ) {
        ScaffoldLayout(
            fabPosition = floatingActionButtonPosition,
            topBar = { topBar(topBarScrollBehavior) },
            startBar = startBar,
            bottomBar = bottomBar,
            content = content,
            snackbar = snackbarHost,
            contentWindowInsets = contentWindowInsets,
            fab = floatingActionButton,
        )
    }
}

/**
 * Layout for a [Scaffold]'s content.
 *
 * @param fabPosition [FabPosition] for the FAB (if present)
 * @param topBar the content to place at the top of the [Scaffold], typically a [SmallTopAppBar]
 * @param content the main 'body' of the [Scaffold]
 * @param snackbar the [Snackbar] displayed on top of the [content]
 * @param fab the [FloatingActionButton] displayed on top of the [content], below the [snackbar]
 * and above the [bottomBar]
 * @param bottomBar the content to place at the bottom of the [Scaffold], on top of the
 * [content], typically a [NavigationBar].
 */
@Composable
private fun ScaffoldLayout(
    fabPosition: FabPosition,
    topBar: @Composable () -> Unit,
    startBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit,
) {
    // Create the backing values for the content padding
    // These values will be updated during measurement, but before measuring and placing
    // the body content
    var topContentPadding by remember { mutableStateOf(0.dp) }
    var startContentPadding by remember { mutableStateOf(0.dp) }
    var endContentPadding by remember { mutableStateOf(0.dp) }
    var bottomContentPadding by remember { mutableStateOf(0.dp) }

    val contentPadding = remember {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                when (layoutDirection) {
                    LayoutDirection.Ltr -> startContentPadding
                    LayoutDirection.Rtl -> endContentPadding
                }

            override fun calculateTopPadding(): Dp = topContentPadding

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                when (layoutDirection) {
                    LayoutDirection.Ltr -> endContentPadding
                    LayoutDirection.Rtl -> startContentPadding
                }

            override fun calculateBottomPadding(): Dp = bottomContentPadding
        }
    }
    Layout(
        contents = listOf(
            { Spacer(Modifier.windowInsetsTopHeight(contentWindowInsets)) },
            { Spacer(Modifier.windowInsetsBottomHeight(contentWindowInsets)) },
            { Spacer(Modifier.windowInsetsStartWidth(contentWindowInsets)) },
            { Spacer(Modifier.windowInsetsEndWidth(contentWindowInsets)) },
            startBar,
            topBar,
            snackbar,
            fab,
            bottomBar,
            { content(contentPadding) },
        ),
    ) { measurables, constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        /**
         * Tachiyomi: Remove height constraint for expanded app bar
         */
        val topBarConstraints = looseConstraints.copy(maxHeight = Constraints.Infinity)

        val topInsetsPlaceables = measurables[0].single()
            .measure(looseConstraints)
        val bottomInsetsPlaceables = measurables[1].single()
            .measure(looseConstraints)
        val startInsetsPlaceables = measurables[2].single()
            .measure(looseConstraints)
        val endInsetsPlaceables = measurables[3].single()
            .measure(looseConstraints)

        val startInsetsWidth = startInsetsPlaceables.width
        val endInsetsWidth = endInsetsPlaceables.width

        val topInsetsHeight = topInsetsPlaceables.height
        val bottomInsetsHeight = bottomInsetsPlaceables.height

        // Tachiyomi: Add startBar slot for Navigation Rail
        val startBarPlaceables = measurables[4]
            .fastMap { it.measure(looseConstraints) }

        val startBarWidth = startBarPlaceables.fastMaxBy { it.width }?.width ?: 0

        val topBarPlaceables = measurables[5]
            .fastMap { it.measure(topBarConstraints) }

        val topBarHeight = topBarPlaceables.fastMaxBy { it.height }?.height ?: 0

        val bottomPlaceablesConstraints = looseConstraints.offset(
            -startInsetsWidth - endInsetsWidth,
            -bottomInsetsHeight,
        )

        val snackbarPlaceables = measurables[6]
            .fastMap { it.measure(bottomPlaceablesConstraints) }

        val snackbarHeight = snackbarPlaceables.fastMaxBy { it.height }?.height ?: 0
        val snackbarWidth = snackbarPlaceables.fastMaxBy { it.width }?.width ?: 0

        val fabPlaceables = measurables[7]
            .fastMap { it.measure(bottomPlaceablesConstraints) }

        val fabWidth = fabPlaceables.fastMaxBy { it.width }?.width ?: 0
        val fabHeight = fabPlaceables.fastMaxBy { it.height }?.height ?: 0

        val fabPlacement = if (fabWidth > 0 && fabHeight > 0) {
            // FAB distance from the left of the layout, taking into account LTR / RTL
            val fabLeftOffset = when (fabPosition) {
                FabPosition.Start -> {
                    if (layoutDirection == LayoutDirection.Ltr) {
                        FabSpacing.roundToPx()
                    } else {
                        layoutWidth - FabSpacing.roundToPx() - fabWidth
                    }
                }
                FabPosition.End, FabPosition.EndOverlay -> {
                    if (layoutDirection == LayoutDirection.Ltr) {
                        layoutWidth - FabSpacing.roundToPx() - fabWidth
                    } else {
                        FabSpacing.roundToPx()
                    }
                }
                else -> (layoutWidth - fabWidth) / 2
            }

            FabPlacement(
                left = fabLeftOffset,
                width = fabWidth,
                height = fabHeight,
            )
        } else {
            null
        }

        val bottomBarPlaceables = measurables[8]
            .fastMap { it.measure(looseConstraints) }

        val bottomBarHeight = bottomBarPlaceables.fastMaxBy { it.height }?.height ?: 0

        val fabOffsetFromBottom = fabPlacement?.let {
            if (fabPosition == FabPosition.EndOverlay) {
                it.height + FabSpacing.roundToPx() + bottomInsetsHeight
            } else {
                // Total height is the bottom bar height + the FAB height + the padding
                // between the FAB and bottom bar
                max(bottomBarHeight, bottomInsetsHeight) + it.height + FabSpacing.roundToPx()
            }
        }
        val snackbarOffsetFromBottom = if (snackbarHeight != 0) {
            snackbarHeight + max(
                fabOffsetFromBottom ?: 0,
                max(
                    bottomBarHeight,
                    bottomInsetsHeight,
                ),
            )
        } else {
            0
        }

        // Update the backing value for the content padding of the body content
        // We do this before measuring or placing the body content
        topContentPadding = max(topBarHeight, topInsetsHeight).toDp()
        bottomContentPadding = max(fabOffsetFromBottom ?: 0, max(bottomBarHeight, bottomInsetsHeight)).toDp()
        startContentPadding = max(startBarWidth, startInsetsWidth).toDp()
        endContentPadding = endInsetsWidth.toDp()

        val bodyContentPlaceables = measurables[9]
            .fastMap { it.measure(looseConstraints) }

        layout(layoutWidth, layoutHeight) {
            // Inset spacers are just for convenient measurement logic, no need to place them
            // Placing to control drawing order to match default elevation of each placeable
            bodyContentPlaceables.fastForEach {
                it.place(0, 0)
            }
            startBarPlaceables.fastForEach {
                it.placeRelative(0, 0)
            }
            topBarPlaceables.fastForEach {
                it.place(0, 0)
            }
            snackbarPlaceables.fastForEach {
                it.place(
                    (layoutWidth - snackbarWidth) / 2 + when (layoutDirection) {
                        LayoutDirection.Ltr -> startInsetsWidth
                        LayoutDirection.Rtl -> endInsetsWidth
                    },
                    layoutHeight - snackbarOffsetFromBottom,
                )
            }
            // The bottom bar is always at the bottom of the layout
            bottomBarPlaceables.fastForEach {
                it.place(0, layoutHeight - bottomBarHeight)
            }
            // Explicitly not using placeRelative here as `leftOffset` already accounts for RTL
            fabPlacement?.let { placement ->
                fabPlaceables.fastForEach {
                    it.place(placement.left, layoutHeight - fabOffsetFromBottom!!)
                }
            }
        }
    }
}

/**
 * Placement information for a [FloatingActionButton] inside a [Scaffold].
 *
 * @property left the FAB's offset from the left edge of the bottom bar, already adjusted for RTL
 * support
 * @property width the width of the FAB
 * @property height the height of the FAB
 */
@Immutable
internal class FabPlacement(
    val left: Int,
    val width: Int,
    val height: Int,
)

// FAB spacing above the bottom bar / bottom of the Scaffold
private val FabSpacing = 16.dp
