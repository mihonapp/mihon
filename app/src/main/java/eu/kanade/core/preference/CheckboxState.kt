package eu.kanade.core.preference

import androidx.compose.ui.state.ToggleableState
import tachiyomi.core.preference.CheckboxState

fun <T> CheckboxState.TriState<T>.asToggleableState() = when (this) {
    is CheckboxState.TriState.Exclude -> ToggleableState.Indeterminate
    is CheckboxState.TriState.Include -> ToggleableState.On
    is CheckboxState.TriState.None -> ToggleableState.Off
}
