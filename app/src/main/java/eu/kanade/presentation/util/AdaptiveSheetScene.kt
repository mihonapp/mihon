package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

data class AdaptiveSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
) : OverlayScene<T> {

    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries
    override val content: @Composable () -> Unit = { entry.Content() }

    companion object {
        fun adaptiveSheet() = metadata {
            put(AdaptiveSheetKey, true)
        }
    }

    object AdaptiveSheetKey : NavMetadataKey<Boolean>
}

@Composable
fun <T : Any> rememberAdaptiveSheetSceneStrategy(): AdaptiveSheetSceneStrategy<T> {
    return remember { AdaptiveSheetSceneStrategy() }
}

class AdaptiveSheetSceneStrategy<T: Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
            ?.takeIf { it.metadata.contains(AdaptiveSheetScene.AdaptiveSheetKey) }
            ?: return null

        return AdaptiveSheetScene(
            key = lastEntry.contentKey,
            previousEntries = entries.dropLast(1),
            entry = lastEntry,
        )
    }
}
