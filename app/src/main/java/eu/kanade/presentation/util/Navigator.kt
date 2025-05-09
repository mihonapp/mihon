package eu.kanade.presentation.util

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.ScreenModelStore
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import eu.kanade.tachiyomi.util.view.getWindowRadius
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.presentation.core.util.PredictiveBack
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue

/**
 * For invoking back press to the parent activity
 */
@SuppressLint("ComposeCompositionLocalUsage")
val LocalBackPress: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

interface Tab : cafe.adriel.voyager.navigator.tab.Tab {
    suspend fun onReselect(navigator: Navigator) {}
}

abstract class Screen : Screen {

    override val key: ScreenKey = uniqueScreenKey
}

/**
 * A variant of ScreenModel.coroutineScope except with the IO dispatcher instead of the
 * main dispatcher.
 */
val ScreenModel.ioCoroutineScope: CoroutineScope
    get() = ScreenModelStore.getOrPutDependency(
        screenModel = this,
        name = "ScreenModelIoCoroutineScope",
        factory = { key -> CoroutineScope(Dispatchers.IO + SupervisorJob()) + CoroutineName(key) },
        onDispose = { scope -> scope.cancel() },
    )

interface AssistContentScreen {
    fun onProvideAssistUrl(): String?
}

@OptIn(InternalVoyagerApi::class)
@Composable
fun DefaultNavigatorScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val screenCandidatesToDispose = rememberSaveable(saver = screenCandidatesToDisposeSaver()) {
        mutableStateOf(emptySet())
    }
    val currentScreens = navigator.items
    DisposableEffect(currentScreens) {
        onDispose {
            val newScreenKeys = navigator.items.map { it.key }
            screenCandidatesToDispose.value += currentScreens.filter { it.key !in newScreenKeys }
        }
    }

    val slideDistance = rememberSlideDistance(slideDistance = 30.dp)
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        enterTransition = {
            if (it == SwipeEdge.Right) {
                materialSharedAxisXIn(forward = false, slideDistance = slideDistance)
            } else {
                materialSharedAxisXIn(forward = true, slideDistance = slideDistance)
            }
        },
        exitTransition = {
            if (it == SwipeEdge.Right) {
                materialSharedAxisXOut(forward = false, slideDistance = slideDistance)
            } else {
                materialSharedAxisXOut(forward = true, slideDistance = slideDistance)
            }
        },
        popEnterTransition = {
            if (it == SwipeEdge.Right) {
                materialSharedAxisXIn(forward = true, slideDistance = slideDistance)
            } else {
                materialSharedAxisXIn(forward = false, slideDistance = slideDistance)
            }
        },
        popExitTransition = {
            if (it == SwipeEdge.Right) {
                materialSharedAxisXOut(forward = true, slideDistance = slideDistance)
            } else {
                materialSharedAxisXOut(forward = false, slideDistance = slideDistance)
            }
        },
        content = { screen ->
            if (this.transition.targetState == this.transition.currentState) {
                LaunchedEffect(Unit) {
                    val newScreens = navigator.items.map { it.key }
                    val screensToDispose = screenCandidatesToDispose.value.filterNot { it.key in newScreens }
                    if (screensToDispose.isNotEmpty()) {
                        screensToDispose.forEach { navigator.dispose(it) }
                        navigator.clearEvent()
                    }
                    screenCandidatesToDispose.value = emptySet()
                }
            }
            screen.Content()
        },
    )
}

enum class SwipeEdge {
    Unknown,
    Left,
    Right,
}

private enum class AnimationType {
    Pop,
    Cancel,
}

@Composable
fun ScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    enterTransition: AnimatedContentTransitionScope<Screen>.(SwipeEdge) -> EnterTransition = { fadeIn() },
    exitTransition: AnimatedContentTransitionScope<Screen>.(SwipeEdge) -> ExitTransition = { fadeOut() },
    popEnterTransition: AnimatedContentTransitionScope<Screen>.(SwipeEdge) -> EnterTransition = enterTransition,
    popExitTransition: AnimatedContentTransitionScope<Screen>.(SwipeEdge) -> ExitTransition = exitTransition,
    sizeTransform: (AnimatedContentTransitionScope<Screen>.() -> SizeTransform?)? = null,
    flingAnimationSpec: () -> AnimationSpec<Float> = { spring(stiffness = Spring.StiffnessLow) },
    content: ScreenTransitionContent = { it.Content() },
) {
    val view = LocalView.current
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val state = remember {
        ScreenTransitionState(
            navigator = navigator,
            scope = scope,
            flingAnimationSpec = flingAnimationSpec(),
            windowCornerRadius = view.getWindowRadius().toFloat(),
        )
    }
    val transitionState = remember { SeekableTransitionState(navigator.lastItem) }
    val transition = rememberTransition(transitionState = transitionState)

    if (state.isPredictiveBack || state.isAnimating) {
        LaunchedEffect(state.progress) {
            if (!state.isPredictiveBack) return@LaunchedEffect
            val previousEntry = navigator.items.getOrNull(navigator.size - 2)
            if (previousEntry != null) {
                transitionState.seekTo(fraction = state.progress, targetState = previousEntry)
            }
        }
    } else {
        LaunchedEffect(navigator) {
            snapshotFlow { navigator.lastItem }
                .collect {
                    state.cancelCancelAnimation()
                    if (it != transitionState.currentState) {
                        transitionState.animateTo(it)
                    } else {
                        transitionState.snapTo(it)
                    }
                }
        }
    }

    PredictiveBackHandler(enabled = navigator.canPop) { backEvent ->
        state.cancelCancelAnimation()
        var startOffset: Offset? = null
        backEvent
            .dropWhile {
                if (startOffset == null) startOffset = Offset(it.touchX, it.touchY)
                if (state.isAnimating) return@dropWhile true
                // Touch slop check
                val diff = Offset(it.touchX, it.touchY) - startOffset!!
                diff.x.absoluteValue < viewConfig.touchSlop && diff.y.absoluteValue < viewConfig.touchSlop
            }
            .onCompletion {
                if (it == null) {
                    state.finish()
                } else {
                    state.cancel()
                }
            }
            .collect {
                state.setPredictiveBackProgress(
                    progress = it.progress,
                    swipeEdge = when (it.swipeEdge) {
                        BackEventCompat.EDGE_LEFT -> SwipeEdge.Left
                        BackEventCompat.EDGE_RIGHT -> SwipeEdge.Right
                        else -> SwipeEdge.Unknown
                    },
                )
            }
    }

    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val pop = navigator.lastEvent == StackEvent.Pop || state.isPredictiveBack
            ContentTransform(
                targetContentEnter = if (pop) {
                    popEnterTransition(state.swipeEdge)
                } else {
                    enterTransition(state.swipeEdge)
                },
                initialContentExit = if (pop) {
                    popExitTransition(state.swipeEdge)
                } else {
                    exitTransition(state.swipeEdge)
                },
                targetContentZIndex = if (pop) 0f else 1f,
                sizeTransform = sizeTransform?.invoke(this),
            )
        },
        contentKey = { it.key },
    ) {
        navigator.saveableState("transition", it) {
            content(it)
        }
    }
}

@Stable
private class ScreenTransitionState(
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    private val flingAnimationSpec: AnimationSpec<Float>,
    windowCornerRadius: Float,
) {
    var isPredictiveBack: Boolean by mutableStateOf(false)
        private set

    var progress: Float by mutableFloatStateOf(0f)
        private set

    var swipeEdge: SwipeEdge by mutableStateOf(SwipeEdge.Unknown)
        private set

    private var animationJob: Pair<Job, AnimationType>? by mutableStateOf(null)

    val isAnimating: Boolean
        get() = animationJob?.first?.isActive == true

    val windowCornerShape = RoundedCornerShape(windowCornerRadius)

    private fun reset() {
        this.isPredictiveBack = false
        this.swipeEdge = SwipeEdge.Unknown
        this.animationJob = null
    }

    fun setPredictiveBackProgress(progress: Float, swipeEdge: SwipeEdge) {
        this.progress = lerp(0f, 0.65f, PredictiveBack.transform(progress))
        this.swipeEdge = swipeEdge
        this.isPredictiveBack = true
    }

    fun finish() {
        if (!isPredictiveBack) {
            navigator.pop()
            return
        }
        animationJob = scope.launch {
            try {
                animate(
                    initialValue = progress,
                    targetValue = 1f,
                    animationSpec = flingAnimationSpec,
                    block = { i, _ -> progress = i },
                )
                navigator.pop()
            } catch (e: CancellationException) {
                // Cancelled
                progress = 0f
            } finally {
                reset()
            }
        } to AnimationType.Pop
    }

    fun cancel() {
        if (!isPredictiveBack) {
            return
        }
        animationJob = scope.launch {
            try {
                animate(
                    initialValue = progress,
                    targetValue = 0f,
                    animationSpec = flingAnimationSpec,
                    block = { i, _ -> progress = i },
                )
            } catch (e: CancellationException) {
                // Cancelled
                progress = 1f
            } finally {
                reset()
            }
        } to AnimationType.Cancel
    }

    fun cancelCancelAnimation() {
        if (animationJob?.second == AnimationType.Cancel) {
            animationJob?.first?.cancel()
            animationJob = null
        }
    }
}

private fun screenCandidatesToDisposeSaver(): Saver<MutableState<Set<Screen>>, List<Screen>> {
    return Saver(
        save = { it.value.toList() },
        restore = { mutableStateOf(it.toSet()) },
    )
}
