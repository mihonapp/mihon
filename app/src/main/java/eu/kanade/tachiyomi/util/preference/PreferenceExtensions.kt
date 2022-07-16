package eu.kanade.tachiyomi.util.preference

import android.widget.CompoundButton
import com.fredporciuncula.flow.preferences.Preference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Binds a checkbox or switch view with a boolean preference.
 */
fun CompoundButton.bindToPreference(pref: Preference<Boolean>) {
    isChecked = pref.get()
    setOnCheckedChangeListener { _, isChecked -> pref.set(isChecked) }
}

fun <T> Preference<T>.asHotFlow(block: (T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}
