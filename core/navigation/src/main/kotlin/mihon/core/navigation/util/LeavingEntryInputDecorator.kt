package mihon.core.navigation.util

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Drops pointer input on entries animating out, so a repeated tap can't act on a screen that already left.
 */
@Composable
internal fun <T : Any> rememberLeavingEntryInputNavEntryDecorator(): NavEntryDecorator<T> {
    return remember {
        NavEntryDecorator { entry ->
            val leaving = LocalNavAnimatedContentScope.current.transition.targetState == EnterExitState.PostExit
            Box(
                modifier = Modifier.pointerInput(leaving) {
                    if (!leaving) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                },
                propagateMinConstraints = true,
            ) {
                entry.Content()
            }
        }
    }
}
