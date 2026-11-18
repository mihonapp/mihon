package eu.kanade.tachiyomi.ui.manga.track

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.core.navigation.util.LocalNavigator
import mihon.core.navigation.util.NavigatorDisplay
import mihon.core.navigation.util.rememberNavigator

@Composable
fun TrackInfoDialog(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    onDismissRequest: () -> Unit,
) {
    val sheetNavigator = rememberNavigator(TrackInfoDialogHomeRoute(mangaId, mangaTitle, sourceId))

    CompositionLocalProvider(LocalNavigator provides sheetNavigator) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            enableImplicitDismiss = !sheetNavigator.canPop,
        ) {
            NavigatorDisplay(
                navigator = sheetNavigator,
                sizeTransform = SizeTransform(),
                transitionSpec = { fade },
                popTransitionSpec = { fade },
                predictivePopTransitionSpec = { fade },
                // Otherwise every picker reuses the first one's ViewModel, keyed only by class
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
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
