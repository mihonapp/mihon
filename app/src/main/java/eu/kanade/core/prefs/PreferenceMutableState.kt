package eu.kanade.core.prefs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import eu.kanade.tachiyomi.core.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PreferenceMutableState<T>(
    private val preference: Preference<T>,
    scope: CoroutineScope,
) : MutableState<T> {

    private val state = mutableStateOf(preference.get())

    init {
        preference.changes()
            .onEach { state.value = it }
            .launchIn(scope)
    }

    override var value: T
        get() = state.value
        set(value) {
            preference.set(value)
        }

    override fun component1(): T {
        return state.value
    }

    override fun component2(): (T) -> Unit {
        return { preference.set(it) }
    }
}

fun <T> Preference<T>.asState(scope: CoroutineScope) = PreferenceMutableState(this, scope)
