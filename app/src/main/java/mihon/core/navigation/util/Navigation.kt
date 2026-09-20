package mihon.core.navigation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

val LocalBackStack = compositionLocalOf<NavBackStack<NavKey>> {
    error("LocalBackStack not initialized!")
}

val LocalTopLevelBackStack = compositionLocalOf<TopLevelBackStack> {
    error("LocalTopLevelBackStack not initialized!")
}

data class TabOptions(
    val title: String,
    val icon: Painter,
)

fun NavBackStack<NavKey>.replace(source: NavKey) {
    if (isNotEmpty()) removeLastOrNull()
    add(source)
}

fun NavBackStack<NavKey>.popUntil(predicate: (NavKey) -> Boolean) {
    while (isNotEmpty() && !predicate(last())) {
        removeLastOrNull()
    }
}

fun NavBackStack<NavKey>.popUntilRoot() {
    while (size > 1) {
        removeLastOrNull()
    }
}

class TopLevelBackStack(val startKey: TopLevelRoute) {

    // Expose the current top level route for consumers
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    var backStack: SnapshotStateList<TopLevelRoute> = mutableStateListOf(startKey)
        private set

    fun setTopLevel(key: TopLevelRoute) {
        if (key == startKey) {
            backStack.apply {
                clear()
                add(key)
            }
        } else {
            if (backStack.size == 1) {
                backStack.add(key)
            } else {
                backStack.apply {
                    set(1, key)
                }
            }
        }
        topLevelKey = key
    }

    fun removeLast() {
        backStack.removeAt(backStack.lastIndex)
        topLevelKey = backStack.last()
    }

    internal constructor(stack: SnapshotStateList<TopLevelRoute>) : this(stack.first()) {
        apply {
            backStack = stack
            topLevelKey = stack.last()
        }
    }
}

@Composable
fun rememberTopLevelBackStack(
    startDestination: TopLevelRoute,
): TopLevelBackStack {
    return rememberSerializable(
        serializer = TopLevelBackStackSerializer(elementSerializer = TopLevelRoute.serializer()),
    ) {
        TopLevelBackStack(startDestination)
    }
}

class TopLevelBackStackSerializer(
    elementSerializer: KSerializer<TopLevelRoute>,
) : KSerializer<TopLevelBackStack> {

    private val delegate = SnapshotStateListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor("eu.kanade.presentation.util.TopLevelBackStack", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: TopLevelBackStack) {
        encoder.encodeSerializableValue(serializer = delegate, value = value.backStack)
    }

    override fun deserialize(decoder: Decoder): TopLevelBackStack {
        return TopLevelBackStack(stack = decoder.decodeSerializableValue(deserializer = delegate))
    }
}
