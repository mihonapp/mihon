package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import androidx.navigation3.ui.NavDisplay
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseSwitchToExtensionEventKey
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.library.LibraryTabSearchEventKey
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import mihon.app.di.appGraph
import mihon.core.navigation.DownloadQueueRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.util.LocalBackStack
import mihon.core.navigation.util.LocalTopLevelBackStack
import mihon.core.navigation.util.TabOptions
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Serializable
sealed interface TopLevelRoute : NavKey {

    @Composable
    fun options(isSelected: Boolean): TabOptions

    @Serializable
    data object Library : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object Updates : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object History : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object Browse : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }

    @Serializable
    data object More : TopLevelRoute {
        @Composable
        override fun options(isSelected: Boolean): TabOptions {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    }
}

@Composable
fun HomeScreen() {
    val topLevelBackStack = LocalTopLevelBackStack.current
    val backStack = LocalBackStack.current
    val resultEventBus = LocalResultEventBus.current
    val tabletUi = isTabletUi()
    val navigationSuiteType = if (tabletUi) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }
    val navigationSuiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(navigationSuiteState, tabletUi) {
        if (tabletUi) navigationSuiteState.show()
    }

    ResultEffect<ShowBottomNavEvent> {
        if (tabletUi || it.visible) {
            navigationSuiteState.show()
        } else {
            navigationSuiteState.hide()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteType = navigationSuiteType,
        state = navigationSuiteState,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationRailContainerColor = MaterialTheme.colorScheme
                .surfaceColorAtElevation(3.dp),
        ),
        navigationItemVerticalArrangement = Arrangement.Center,
        navigationItems = {
            TABS.fastForEach { NavigationSuiteItem(it, navigationSuiteType) }
        },
    ) {
        NavDisplay(
            backStack = topLevelBackStack.backStack,
            onBack = {
                topLevelBackStack.removeLast()
            },
            transitionSpec = { spec },
            popTransitionSpec = { spec },
            predictivePopTransitionSpec = { spec },
            entryProvider = entryProvider {
                entry<TopLevelRoute.Library> {
                    LibraryTab()
                }

                entry<TopLevelRoute.Updates> {
                    UpdatesTab()
                }

                entry<TopLevelRoute.History> {
                    HistoryTab()
                }

                entry<TopLevelRoute.Browse> {
                    BrowseTab()
                }

                entry<TopLevelRoute.More> {
                    MoreTab()
                }
            },
        )
    }

    ResultEffect<String>(resultKey = LibrarySearchEventKey) {
        topLevelBackStack.setTopLevel(TopLevelRoute.Library)
        resultEventBus.sendResult(
            resultKey = LibraryTabSearchEventKey,
            result = it,
        )
    }

    ResultEffect<TabEvent> {
        val route = when (it) {
            is TabEvent.Library -> TopLevelRoute.Library
            TabEvent.Updates -> TopLevelRoute.Updates
            TabEvent.History -> TopLevelRoute.History
            is TabEvent.Browse -> {
                if (it.toExtensions) {
                    resultEventBus.sendResult(
                        resultKey = BrowseSwitchToExtensionEventKey,
                        result = Unit,
                    )
                }
                TopLevelRoute.Browse
            }
            is TabEvent.More -> TopLevelRoute.More
        }
        topLevelBackStack.setTopLevel(route)

        if (it is TabEvent.Library && it.mangaIdToOpen != null) {
            backStack.add(MangaRoute(it.mangaIdToOpen))
        }
        if (it is TabEvent.More && it.toDownloads) {
            backStack.add(DownloadQueueRoute)
        }
    }
}

@Composable
private fun NavigationSuiteItem(
    tab: TopLevelRoute,
    navigationSuiteType: NavigationSuiteType,
) {
    val topLevelBackStack = LocalTopLevelBackStack.current
    val resultEventBus = LocalResultEventBus.current
    val selected = topLevelBackStack.topLevelKey == tab
    val options = tab.options(selected)

    NavigationSuiteItem(
        navigationSuiteType = navigationSuiteType,
        selected = selected,
        onClick = {
            if (!selected) {
                topLevelBackStack.setTopLevel(tab)
            } else {
                resultEventBus.sendResult(
                    resultKey = TabReselectEventKey,
                    result = Unit,
                )
            }
        },
        icon = {
            Icon(
                painter = options.icon,
                contentDescription = options.title,
            )
        },
        label = {
            Text(
                text = options.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        badge = tabBadge(tab),
    )
}

@Composable
private fun tabBadge(tab: TopLevelRoute): (@Composable () -> Unit)? {
    val context = LocalContext.current
    val count by produceState(initialValue = 0, tab) {
        val graph = context.appGraph
        when (tab) {
            is TopLevelRoute.Updates -> {
                combine(
                    graph.libraryPreferences.newShowUpdatesCount.changes(),
                    graph.libraryPreferences.newUpdatesCount.changes(),
                ) { show, count ->
                    if (show) count else 0
                }
                    .collectLatest { value = it }
            }

            is TopLevelRoute.Browse -> {
                graph.sourcePreferences.extensionUpdatesCount.changes()
                    .collectLatest { value = it }
            }

            else -> value = 0
        }
    }
    if (count <= 0) return null
    return {
        Badge {
            val desc = when (tab) {
                is TopLevelRoute.Updates -> pluralStringResource(
                    MR.plurals.notification_chapters_generic,
                    count = count,
                    count,
                )

                is TopLevelRoute.Browse -> pluralStringResource(
                    MR.plurals.update_check_notification_ext_updates,
                    count = count,
                    count,
                )

                else -> null
            }
            Text(
                text = count.toString(),
                modifier = Modifier.semantics {
                    if (desc != null) contentDescription = desc
                },
            )
        }
    }
}

sealed interface TabEvent {
    data class Library(val mangaIdToOpen: Long? = null) : TabEvent
    data object Updates : TabEvent
    data object History : TabEvent
    data class Browse(val toExtensions: Boolean = false) : TabEvent
    data class More(val toDownloads: Boolean) : TabEvent
}

@Suppress("ConstPropertyName")
const val TabReselectEventKey = "TabReselectEventKey"

@Suppress("ConstPropertyName")
const val LibrarySearchEventKey = "LibrarySearchEventKey"

@Suppress("ConstPropertyName")
private const val TabFadeDuration = 200

data class ShowBottomNavEvent(val visible: Boolean)

private val spec = materialFadeThroughIn(
    initialScale = 1f,
    durationMillis = TabFadeDuration,
) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)

// Home screen
private val TABS: List<TopLevelRoute> = listOf(
    TopLevelRoute.Library,
    TopLevelRoute.Updates,
    TopLevelRoute.History,
    TopLevelRoute.Browse,
    TopLevelRoute.More,
)
