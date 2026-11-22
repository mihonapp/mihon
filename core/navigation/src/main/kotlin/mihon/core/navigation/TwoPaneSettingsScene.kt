package mihon.core.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import mihon.presentation.core.util.isTabletUi
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.presentation.core.components.TwoPanelBox

val LocalBackButtonVisibility = compositionLocalOf { true }

/**
 * A custom [Scene] that displays two [NavEntry]s side-by-side in a split.
 */
data class TwoPaneSettingsScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val firstEntry: NavEntry<T>,
    val secondEntry: NavEntry<T>,
    private val detailDepth: Int,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(firstEntry, secondEntry)
    override val content: @Composable (() -> Unit) = {
        val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
        TwoPanelBox(
            startContent = { firstEntry.Content() },
            endContent = {
                CompositionLocalProvider(LocalBackButtonVisibility provides false) {
                    val slideDistance = rememberSlideDistance()
                    AnimatedContent(
                        targetState = secondEntry to detailDepth,
                        contentKey = { (entry) -> entry.contentKey },
                        transitionSpec = {
                            materialSharedAxisX(
                                forward = targetState.second >= initialState.second,
                                slideDistance = slideDistance,
                            )
                        },
                    ) { (entry) ->
                        entry.Content()
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets)
                .consumeWindowInsets(insets),
        )
    }

    companion object {
        /**
         * Helper function to add metadata to a [NavEntry] indicating that it's a list screen
         * displayed to the left
         */
        fun listPane() = metadata {
            put(ListKey, true)
        }

        /**
         * Helper function to add metadata to a [NavEntry] that always opens full screen, even when
         * pushed while settings are open
         */
        fun fullScreen() = metadata {
            put(FullScreenKey, true)
        }
    }

    object ListKey : NavMetadataKey<Boolean>

    object FullScreenKey : NavMetadataKey<Boolean>
}

@Composable
fun <T : Any> rememberTwoPaneSettingsSceneStrategy(): TwoPaneSettingsSceneStrategy<T> {
    val isTabletUi = isTabletUi()

    return remember(isTabletUi) {
        TwoPaneSettingsSceneStrategy(isTabletUi)
    }
}

/**
 * A [SceneStrategy] that activates a [TwoPaneSettingsScene] if in tablet ui
 * and the top two back stack entries includes a list entry
 */
class TwoPaneSettingsSceneStrategy<T : Any>(val isTabletUi: Boolean) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // Condition 1: Only return a Scene if the window is in tablet ui
        if (!isTabletUi) {
            return null
        }

        val detailEntry =
            entries.lastOrNull()?.takeUnless { it.metadata.contains(TwoPaneSettingsScene.ListKey) } ?: return null
        val listEntry = entries.findLast { it.metadata.contains(TwoPaneSettingsScene.ListKey) } ?: return null
        val details = entries.subList(entries.lastIndexOf(listEntry) + 1, entries.size)
        if (details.any { it.metadata.contains(TwoPaneSettingsScene.FullScreenKey) }) return null

        // We use the list's contentKey to uniquely identify the scene.
        // This allows the detail panes to be animated in and out by the scene, rather than
        // having NavDisplay animate the whole scene out when the selected detail item changes.
        val sceneKey = listEntry.contentKey

        // Popping a detail opened from another detail goes back to that one, not past the list
        val popped = if (entries[entries.lastIndex - 1] === listEntry) 2 else 1

        return TwoPaneSettingsScene(
            key = sceneKey,
            previousEntries = entries.dropLast(popped),
            firstEntry = listEntry,
            secondEntry = detailEntry,
            detailDepth = details.size,
        )
    }
}
