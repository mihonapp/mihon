package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.core.preference.Preference

@Composable
fun <T> Preference<T>.collectAsState(): State<T> {
    val flow = remember(this) { changes() }
    return flow.collectAsState(initial = get())
}
