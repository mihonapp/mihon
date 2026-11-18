package mihon.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import java.util.concurrent.ConcurrentHashMap

class AssistContentManager {
    private val urls = ConcurrentHashMap<NavKey, String>()

    fun urlFor(route: NavKey, routes: List<NavKey>): String? {
        urls.keys.retainAll(routes.toSet())
        return urls[route]
    }

    @Composable
    fun Provide(route: NavKey, content: @Composable () -> Unit) {
        val setUrl = remember(route) {
            { url: String? ->
                if (url == null) urls.remove(route) else urls[route] = url
                Unit
            }
        }
        CompositionLocalProvider(LocalAssistUrl provides setUrl, content = content)
    }
}

val LocalAssistContentManager = staticCompositionLocalOf<AssistContentManager> {
    error("LocalAssistContentManager not initialized!")
}

val LocalAssistUrl = staticCompositionLocalOf<(String?) -> Unit> { {} }
