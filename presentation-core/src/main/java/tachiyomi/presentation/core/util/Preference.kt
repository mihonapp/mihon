package tachiyomi.presentation.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import tachiyomi.core.common.preference.Preference

@Composable
fun <T> Preference<T>.collectAsState(scope: CoroutineScope = rememberCoroutineScope()): State<T> {
    val flow = remember(this) { stateIn(scope) }
    return flow.collectAsState()
}
