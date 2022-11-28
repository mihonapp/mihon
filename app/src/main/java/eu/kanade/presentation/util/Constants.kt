package eu.kanade.presentation.util

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.with
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

val topSmallPaddingValues = PaddingValues(top = MaterialTheme.padding.small)

const val ReadItemAlpha = .38f
const val SecondaryItemAlpha = .78f

class Padding {

    val extraLarge = 32.dp

    val large = 24.dp

    val medium = 16.dp

    val small = 8.dp

    val tiny = 4.dp
}

val MaterialTheme.padding: Padding
    get() = Padding()

object Transition {

    /**
     * Mimics [eu.kanade.tachiyomi.ui.base.controller.OneWayFadeChangeHandler]
     */
    val OneWayFade = fadeIn(
        animationSpec = tween(
            easing = LinearEasing,
        ),
    ) with ExitTransition.None
}
