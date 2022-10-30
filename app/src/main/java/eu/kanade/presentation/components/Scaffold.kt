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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
 * * Fixes for fab and snackbar horizontal placements when [contentWindowInsets] is used
 *
 * @param modifier the [Modifier] to be applied to this scaffold
 * @param topBar top app bar of the screen, typically a [SmallTopAppBar]
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
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
    topBar: @Composable (TopAppBarScrollBehavior) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldLayout(
    fabPosition: FabPosition,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
    snackbar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    contentWindowInsets: WindowInsets,
    bottomBar: @Composable () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        /**
         * Tachiyomi: Remove height constraint for expanded app bar
         */
        val topBarConstraints = looseConstraints.copy(maxHeight = Constraints.Infinity)

        layout(layoutWidth, layoutHeight) {
            val leftInset = contentWindowInsets.getLeft(this@SubcomposeLayout, layoutDirection)
            val rightInset = contentWindowInsets.getRight(this@SubcomposeLayout, layoutDirection)
            val bottomInset = contentWindowInsets.getBottom(this@SubcomposeLayout)
            // Tachiyomi: layoutWidth after horizontal insets
            val insetLayoutWidth = layoutWidth - leftInset - rightInset

            val topBarPlaceables = subcompose(ScaffoldLayoutContent.TopBar, topBar).fastMap {
                it.measure(topBarConstraints)
            }

            val topBarHeight = topBarPlaceables.fastMaxBy { it.height }?.height ?: 0

            val snackbarPlaceables = subcompose(ScaffoldLayoutContent.Snackbar, snackbar).fastMap {
                it.measure(looseConstraints)
            }

            val snackbarHeight = snackbarPlaceables.fastMaxBy { it.height }?.height ?: 0
            val snackbarWidth = snackbarPlaceables.fastMaxBy { it.width }?.width ?: 0

            // Tachiyomi: Calculate insets for snackbar placement offset
            val snackbarLeft = if (snackbarPlaceables.isNotEmpty()) {
                (insetLayoutWidth - snackbarWidth) / 2 + leftInset
            } else {
                0
            }

            val fabPlaceables =
                subcompose(ScaffoldLayoutContent.Fab, fab).fastMap { measurable ->
                    measurable.measure(looseConstraints)
                }

            val fabWidth = fabPlaceables.fastMaxBy { it.width }?.width ?: 0
            val fabHeight = fabPlaceables.fastMaxBy { it.height }?.height ?: 0

            val fabPlacement = if (fabPlaceables.isNotEmpty() && fabWidth != 0 && fabHeight != 0) {
                // FAB distance from the left of the layout, taking into account LTR / RTL
                // Tachiyomi: Calculate insets for fab placement offset
                val fabLeftOffset = if (fabPosition == FabPosition.End) {
                    if (layoutDirection == LayoutDirection.Ltr) {
                        layoutWidth - FabSpacing.roundToPx() - fabWidth - rightInset
                    } else {
                        FabSpacing.roundToPx() + leftInset
                    }
                } else {
                    leftInset + ((insetLayoutWidth - fabWidth) / 2)
                }

                FabPlacement(
                    left = fabLeftOffset,
                    width = fabWidth,
                    height = fabHeight,
                )
            } else {
                null
            }

            val bottomBarPlaceables = subcompose(ScaffoldLayoutContent.BottomBar) {
                CompositionLocalProvider(
                    LocalFabPlacement provides fabPlacement,
                    content = bottomBar,
                )
            }.fastMap { it.measure(looseConstraints) }

            val bottomBarHeight = bottomBarPlaceables.fastMaxBy { it.height }?.height
            val fabOffsetFromBottom = fabPlacement?.let {
                max(bottomBarHeight ?: 0, bottomInset) + it.height + FabSpacing.roundToPx()
            }

            val snackbarOffsetFromBottom = if (snackbarHeight != 0) {
                snackbarHeight + (fabOffsetFromBottom ?: bottomBarHeight ?: bottomInset)
            } else {
                0
            }

            val bodyContentPlaceables = subcompose(ScaffoldLayoutContent.MainContent) {
                val insets = contentWindowInsets.asPaddingValues(this@SubcomposeLayout)
                val fabOffsetDp = fabOffsetFromBottom?.toDp() ?: 0.dp
                val bottomBarHeightPx = bottomBarHeight ?: 0
                val innerPadding = PaddingValues(
                    top =
                    if (topBarPlaceables.isEmpty()) {
                        insets.calculateTopPadding()
                    } else {
                        topBarHeight.toDp()
                    },
                    bottom = // Tachiyomi: Also take account of fab height when providing inner padding
                    if (bottomBarPlaceables.isEmpty() || bottomBarHeightPx == 0) {
                        max(insets.calculateBottomPadding(), fabOffsetDp)
                    } else {
                        max(bottomBarHeightPx.toDp(), fabOffsetDp)
                    },
                    start = insets.calculateStartPadding((this@SubcomposeLayout).layoutDirection),
                    end = insets.calculateEndPadding((this@SubcomposeLayout).layoutDirection),
                )
                content(innerPadding)
            }.fastMap { it.measure(looseConstraints) }

            // Placing to control drawing order to match default elevation of each placeable

            bodyContentPlaceables.fastForEach {
                it.place(0, 0)
            }
            topBarPlaceables.fastForEach {
                it.place(0, 0)
            }
            snackbarPlaceables.fastForEach {
                it.place(
                    snackbarLeft,
                    layoutHeight - snackbarOffsetFromBottom,
                )
            }
            // The bottom bar is always at the bottom of the layout
            bottomBarPlaceables.fastForEach {
                it.place(0, layoutHeight - (bottomBarHeight ?: 0))
            }
            // Explicitly not using placeRelative here as `leftOffset` already accounts for RTL
            fabPlaceables.fastForEach {
                it.place(fabPlacement?.left ?: 0, layoutHeight - (fabOffsetFromBottom ?: 0))
            }
        }
    }
}

/**
 * The possible positions for a [FloatingActionButton] attached to a [Scaffold].
 */
@ExperimentalMaterial3Api
@JvmInline
value class FabPosition internal constructor(@Suppress("unused") private val value: Int) {
    companion object {
        /**
         * Position FAB at the bottom of the screen in the center, above the [NavigationBar] (if it
         * exists)
         */
        val Center = FabPosition(0)

        /**
         * Position FAB at the bottom of the screen at the end, above the [NavigationBar] (if it
         * exists)
         */
        val End = FabPosition(1)
    }

    override fun toString(): String {
        return when (this) {
            Center -> "FabPosition.Center"
            else -> "FabPosition.End"
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

/**
 * CompositionLocal containing a [FabPlacement] that is used to calculate the FAB bottom offset.
 */
internal val LocalFabPlacement = staticCompositionLocalOf<FabPlacement?> { null }

// FAB spacing above the bottom bar / bottom of the Scaffold
private val FabSpacing = 16.dp

private enum class ScaffoldLayoutContent { TopBar, MainContent, Snackbar, Fab, BottomBar }
