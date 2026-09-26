package eu.kanade.tachiyomi.ui.manga.track

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.core.navigation.util.LocalBackStack

@Composable
fun TrackInfoDialog(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val sheetBackStack = rememberNavBackStack(TrackInfoDialogHomeRoute(mangaId, mangaTitle, sourceId))

    CompositionLocalProvider(LocalBackStack provides sheetBackStack) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            enableImplicitDismiss = sheetBackStack.size == 1,
        ) {
            NavDisplay(
                backStack = sheetBackStack,
                onBack = { sheetBackStack.removeLastOrNull() },
                sizeTransform = SizeTransform(),
                transitionSpec = { fade },
                popTransitionSpec = { fade },
                predictivePopTransitionSpec = { fade },
                entryProvider = entryProvider {
                    entry<TrackerRemoveRoute> { route ->
                        TrackerRemoveScreen(route.mangaId, route.track, route.serviceId)
                    }
                    entry<TrackerSearchRoute> { route ->
                        TrackerSearchScreen(route.mangaId, route.initialQuery, route.currentUrl, route.serviceId)
                    }
                    entry<TrackChapterSelectorRoute> { route ->
                        TrackChapterSelectorScreen(route.track, route.serviceId)
                    }
                    entry<TrackDateRemoverRoute> { route ->
                        TrackDateRemoverScreen(route.track, route.serviceId, route.start)
                    }
                    entry<TrackDateSelectorRoute> { route ->
                        TrackDateSelectorScreen(route.track, route.serviceId, route.start)
                    }
                    entry<TrackInfoDialogHomeRoute> { route ->
                        TrackInfoDialogHomeScreen(route.mangaId, route.mangaTitle, route.sourceId)
                    }
                    entry<TrackScoreSelectorRoute> { route ->
                        TrackScoreSelectorScreen(route.track, route.serviceId)
                    }
                    entry<TrackStatusSelectorRoute> { route ->
                        TrackStatusSelectorScreen(route.track, route.serviceId)
                    }
                },
            )
        }
    }
}

private val fade = fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))
