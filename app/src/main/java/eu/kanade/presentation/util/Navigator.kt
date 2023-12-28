package eu.kanade.presentation.util

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import soup.compose.material.motion.MotionConstants
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.sin

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

@Composable
fun DefaultNavigatorScreenTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val handler = remember {
        OnBackHandler(
            scope = scope,
            windowCornerRadius = view.getWindowRadius(),
            onBackPressed = navigator::pop,
        )
    }
    PredictiveBackHandler(enabled = navigator.canPop) { progress ->
        progress
            .onStart { handler.reset() }
            .onCompletion { e ->
                if (e == null) {
                    handler.onBackConfirmed()
                } else {
                    handler.onBackCancelled()
                }
            }
            .collect(handler::onBackEvent)
    }

    Box(modifier = modifier.onSizeChanged { handler.updateContainerSize(it.toSize()) }) {
        val currentSceneEntry = navigator.lastItem
        val showPrev by remember {
            derivedStateOf { handler.scale < 1f || handler.translationY != 0f }
        }
        val visibleItems = remember(currentSceneEntry, showPrev) {
            if (showPrev) {
                val prevSceneEntry = navigator.items.getOrNull(navigator.size - 2)
                listOfNotNull(currentSceneEntry, prevSceneEntry)
            } else {
                listOfNotNull(currentSceneEntry)
            }
        }

        val slideDistance = rememberSlideDistance()

        val screenContent = remember {
            movableContentOf<Screen> { screen ->
                navigator.saveableState("transition", screen) {
                    screen.Content()
                }
            }
        }

        visibleItems.forEachIndexed { index, backStackEntry ->
            val isPrev = index == 1 && visibleItems.size > 1
            if (!isPrev) {
                AnimatedContent(
                    targetState = backStackEntry,
                    transitionSpec = {
                        val forward = navigator.lastEvent != StackEvent.Pop
                        if (!forward && !handler.isReady) {
                            // Pop screen without animation when predictive back is in use
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            materialSharedAxisX(
                                forward = forward,
                                slideDistance = slideDistance,
                            )
                        }
                    },
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            this.alpha = handler.alpha
                            this.transformOrigin = TransformOrigin(
                                pivotFractionX = if (handler.swipeEdge == BackEventCompat.EDGE_LEFT) 0.8f else 0.2f,
                                pivotFractionY = 0.5f,
                            )
                            this.scaleX = handler.scale
                            this.scaleY = handler.scale
                            this.translationY = handler.translationY
                            this.clip = true
                            this.shape = if (showPrev) {
                                RoundedCornerShape(handler.windowCornerRadius.toFloat())
                            } else {
                                RectangleShape
                            }
                        }
                        .then(
                            if (showPrev) {
                                Modifier.pointerInput(Unit) {
                                    // Animated content should not be interactive
                                }
                            } else {
                                Modifier
                            },
                        ),
                    content = {
                        if (visibleItems.size == 2 && visibleItems.getOrNull(1) == it) {
                            // Avoid drawing previous screen
                            return@AnimatedContent
                        }
                        screenContent(it)
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .zIndex(0f)
                        .drawWithCache {
                            val bounds = Rect(Offset.Zero, size)
                            val matrix = ColorMatrix().apply {
                                // Reduce saturation and brightness
                                setToSaturation(lerp(1f, 0.95f, handler.alpha))
                                set(0, 4, lerp(0f, -25f, handler.alpha))
                                set(1, 4, lerp(0f, -25f, handler.alpha))
                                set(2, 4, lerp(0f, -25f, handler.alpha))
                            }
                            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
                            onDrawWithContent {
                                drawIntoCanvas {
                                    it.saveLayer(bounds, paint)
                                    drawContent()
                                    it.restore()
                                }
                            }
                        }
                        .graphicsLayer {
                            val blurRadius = 5.dp.toPx() * handler.alpha
                            renderEffect = if (blurRadius > 0f) {
                                BlurEffect(blurRadius, blurRadius)
                            } else {
                                null
                            }
                        }
                        .pointerInput(Unit) {
                            // bg content should not be interactive
                        },
                    content = { screenContent(backStackEntry) },
                )
            }
        }

        LaunchedEffect(currentSceneEntry) {
            // Reset *after* the screen is popped successfully
            // so that the correct transition is applied
            handler.setReady()
        }
    }
}

@Stable
private class OnBackHandler(
    private val scope: CoroutineScope,
    val windowCornerRadius: Int,
    private val onBackPressed: () -> Unit,
) {

    var isReady = true
        private set

    var alpha by mutableFloatStateOf(1f)
        private set

    var scale by mutableFloatStateOf(1f)
        private set

    var translationY by mutableFloatStateOf(0f)
        private set

    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        private set

    private var containerSize = Size.Zero
    private var startPointY = Float.NaN

    var isPredictiveBack by mutableStateOf(false)
        private set

    private var animationJob: Job? = null
        set(value) {
            isReady = false
            field = value
        }

    fun updateContainerSize(size: Size) {
        containerSize = size
    }

    fun setReady() {
        reset()
        animationJob?.cancel()
        animationJob = null
        isReady = true
        isPredictiveBack = false
    }

    fun reset() {
        startPointY = Float.NaN
    }

    fun onBackEvent(backEvent: BackEventCompat) {
        if (!isReady) return
        isPredictiveBack = true
        swipeEdge = backEvent.swipeEdge

        val progress = LinearOutSlowInEasing.transform(backEvent.progress)
        scale = lerp(1f, 0.85f, progress)

        if (startPointY.isNaN()) {
            startPointY = backEvent.touchY
        }
        val deltaYRatio = (backEvent.touchY - startPointY) / containerSize.height
        val translateYDistance = containerSize.height / 20
        translationY = sin(deltaYRatio * PI * 0.5).toFloat() * translateYDistance * progress
    }

    fun onBackConfirmed() {
        if (!isReady) return
        if (isPredictiveBack) {
            // Continue predictive animation and pop the screen
            val animationSpec = tween<Float>(
                durationMillis = MotionConstants.DefaultMotionDuration,
                easing = FastOutSlowInEasing,
            )
            animationJob = scope.launch {
                try {
                    listOf(
                        async {
                            animate(
                                initialValue = alpha,
                                targetValue = 0f,
                                animationSpec = animationSpec,
                            ) { value, _ ->
                                alpha = value
                            }
                        },
                        async {
                            animate(
                                initialValue = scale,
                                targetValue = scale - 0.05f,
                                animationSpec = animationSpec,
                            ) { value, _ ->
                                scale = value
                            }
                        },
                    ).awaitAll()
                } catch (e: CancellationException) {
                    // no-op
                } finally {
                    onBackPressed()
                    alpha = 1f
                    translationY = 0f
                    scale = 1f
                }
            }
        } else {
            // Pop right away and use default transition
            onBackPressed()
        }
    }

    fun onBackCancelled() {
        // Reset states
        isPredictiveBack = false
        animationJob = scope.launch {
            listOf(
                async {
                    animate(
                        initialValue = scale,
                        targetValue = 1f,
                    ) { value, _ ->
                        scale = value
                    }
                },
                async {
                    animate(
                        initialValue = alpha,
                        targetValue = 1f,
                    ) { value, _ ->
                        alpha = value
                    }
                },
                async {
                    animate(
                        initialValue = translationY,
                        targetValue = 0f,
                    ) { value, _ ->
                        translationY = value
                    }
                },
            ).awaitAll()

            isReady = true
        }
    }
}

@Composable
fun ScreenTransition(
    navigator: Navigator,
    transition: AnimatedContentTransitionScope<Screen>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() },
) {
    AnimatedContent(
        targetState = navigator.lastItem,
        transitionSpec = transition,
        modifier = modifier,
        label = "transition",
    ) { screen ->
        navigator.saveableState("transition", screen) {
            content(screen)
        }
    }
}
