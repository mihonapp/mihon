package eu.kanade.presentation.more.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.presentation.more.settings.widget.EditTextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.MultiSelectListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrackingPreferenceWidget
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val LocalPreferenceHighlighted = compositionLocalOf(structuralEqualityPolicy()) { false }

@Composable
fun StatusWrapper(
    item: Preference.PreferenceItem<*>,
    highlightKey: String?,
    content: @Composable () -> Unit,
) {
    val enabled = item.enabled
    val highlighted = item.title == highlightKey
    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = {
            CompositionLocalProvider(
                LocalPreferenceHighlighted provides highlighted,
                content = content,
            )
        },
    )
}

@Composable
internal fun PreferenceItem(
    item: Preference.PreferenceItem<*>,
    highlightKey: String?,
) {
    val scope = rememberCoroutineScope()
    StatusWrapper(
        item = item,
        highlightKey = highlightKey,
    ) {
        when (item) {
            is Preference.PreferenceItem.SwitchPreference -> {
                val value by item.pref.collectAsState()
                SwitchPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    checked = value,
                    onCheckedChanged = { newValue ->
                        scope.launch {
                            if (item.onValueChanged(newValue)) {
                                item.pref.set(newValue)
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.ListPreference<*> -> {
                val value by item.pref.collectAsState()
                ListPreferenceWidget(
                    value = value,
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { newValue ->
                        scope.launch {
                            if (item.internalOnValueChanged(newValue!!)) {
                                item.internalSet(newValue)
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.BasicListPreference -> {
                ListPreferenceWidget(
                    value = item.value,
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { scope.launch { item.onValueChanged(it) } },
                )
            }
            is Preference.PreferenceItem.MultiSelectListPreference -> {
                val values by item.pref.collectAsState()
                MultiSelectListPreferenceWidget(
                    preference = item,
                    values = values,
                    onValuesChange = { newValues ->
                        scope.launch {
                            if (item.onValueChanged(newValues)) {
                                item.pref.set(newValues.toMutableSet())
                            }
                        }
                    },
                )
            }
            is Preference.PreferenceItem.TextPreference -> {
                TextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    onPreferenceClick = item.onClick,
                )
            }
            is Preference.PreferenceItem.EditTextPreference -> {
                val values by item.pref.collectAsState()
                EditTextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    value = values,
                    onConfirm = {
                        val accepted = item.onValueChanged(it)
                        if (accepted) item.pref.set(it)
                        accepted
                    },
                )
            }
            is Preference.PreferenceItem.AppThemePreference -> {
                val value by item.pref.collectAsState()
                val amoled by Injekt.get<UiPreferences>().themeDarkAmoled().collectAsState()
                AppThemePreferenceWidget(
                    title = item.title,
                    value = value,
                    amoled = amoled,
                    onItemClick = { scope.launch { item.pref.set(it) } },
                )
            }
            is Preference.PreferenceItem.TrackingPreference -> {
                val uName by Injekt.get<PreferenceStore>()
                    .getString(TrackPreferences.trackUsername(item.service.id))
                    .collectAsState()
                item.service.run {
                    TrackingPreferenceWidget(
                        title = item.title,
                        logoRes = getLogo(),
                        logoColor = getLogoColor(),
                        checked = uName.isNotEmpty(),
                        onClick = { if (isLogged) item.logout() else item.login() },
                    )
                }
            }
        }
    }
}
