package mihon.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

val LocalNavigator = compositionLocalOf<Navigator> {
    error("LocalNavigator not initialized!")
}

/**
 * The only way to change a back stack. Its root can't be removed, so [NavigatorDisplay] never gets an empty stack.
 */
@Stable
class Navigator internal constructor(internal val backStack: NavBackStack<NavKey>) {

    val routes: List<NavKey> get() = backStack

    val current: NavKey get() = backStack.last()

    val previous: NavKey? get() = backStack.getOrNull(backStack.lastIndex - 1)

    val canPop: Boolean get() = backStack.size > 1

    /**
     * Skips [route] if it's already on top, so a repeated tap doesn't open it twice.
     */
    fun push(route: NavKey) {
        if (current != route) backStack.add(route)
    }

    fun pop(): Boolean {
        if (!canPop) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /**
     * Replaces the top route, or pushes [route] if only the root is left.
     */
    fun replace(route: NavKey) {
        pop()
        push(route)
    }

    /**
     * Pops the routes above the top-most one matching [predicate]. Does nothing and returns false if none match.
     */
    fun popTo(predicate: (NavKey) -> Boolean): Boolean {
        val index = backStack.indexOfLast(predicate)
        if (index == -1) return false
        while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun popToRoot() {
        while (pop()) Unit
    }

    /**
     * Replaces every route above the root with [route], leaving only the root if [route] is the root.
     */
    fun replaceAll(route: NavKey) {
        popToRoot()
        push(route)
    }
}

@Composable
fun rememberNavigator(root: NavKey): Navigator {
    val backStack = rememberNavBackStack(root)
    return remember(backStack) { Navigator(backStack) }
}

@Composable
fun NavigatorDisplay(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    entryDecorators: List<NavEntryDecorator<NavKey>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val routes = navigator.backStack.toList()
    // Remembered like NavDisplay does, as new entries make a new scene and restart the transition
    val navEntries = remember(routes) {
        val counts = mutableMapOf<Any, Int>()
        routes.map { route ->
            val entry = entryProvider(route)
            val count = (counts[entry.contentKey] ?: 0) + 1
            counts[entry.contentKey] = count
            if (count == 1) return@map entry
            NavEntry(route, contentKey = "${entry.contentKey}#$count", metadata = entry.metadata) { entry.Content() }
        }
    }
    val entries = rememberDecoratedNavEntries(
        entries = navEntries,
        entryDecorators = entryDecorators + rememberLeavingEntryInputNavEntryDecorator(),
    )
    NavDisplay(
        entries = entries,
        modifier = modifier,
        onBack = { navigator.pop() },
        sceneStrategies = sceneStrategies,
        sizeTransform = sizeTransform,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec,
    )
}
