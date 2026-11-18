package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import kotlin.time.Duration.Companion.seconds

/**
 * Preference Screen composable which contains a list of [Preference] items
 * @param items [Preference] items which should be displayed on the preference screen. An item can be a single [PreferenceItem] or a group ([Preference.PreferenceGroup])
 * @param modifier [Modifier] to be applied to the preferenceScreen layout
 */
@Composable
fun PreferenceScreen(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state = rememberLazyListState()
    val highlightKey = SearchableSettings.highlightKey
    val highlighted = highlightKey?.let { items.findHighlighted(it) }
    if (highlightKey != null) {
        LaunchedEffect(Unit) {
            if (highlighted != null) {
                delay(0.5.seconds)
                state.animateScrollToItem(highlighted.index)
            }
            SearchableSettings.highlightKey = null
        }
    }

    ScrollbarLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        items.fastForEachIndexed { i, preference ->
            when (preference) {
                // Create Preference Group
                is Preference.PreferenceGroup -> {
                    if (!preference.visible) return@fastForEachIndexed

                    item {
                        Column {
                            PreferenceGroupHeader(
                                title = preference.title,
                                highlighted = preference === highlighted?.value,
                            )
                        }
                    }
                    items(preference.preferenceItems) { item ->
                        PreferenceItem(
                            item = item,
                            highlighted = item === highlighted?.value,
                        )
                    }
                    item {
                        if (i < items.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // Create Preference Item
                is Preference.PreferenceItem<*, *> -> item {
                    PreferenceItem(
                        item = preference,
                        highlighted = preference === highlighted?.value,
                    )
                }
            }
        }
    }
}

/**
 * Calls [action] for each group heading and preference item that [PreferenceScreen] shows for these preferences,
 * with the [SearchableSettings.HighlightKey] that identifies it and its index in the list.
 */
internal fun List<Preference>.forEachRow(
    action: (preference: Preference, key: SearchableSettings.HighlightKey, row: Int) -> Unit,
) {
    val occurrences = mutableMapOf<SearchableSettings.HighlightKey, Int>()
    fun keyOf(title: String, isHeading: Boolean): SearchableSettings.HighlightKey {
        val firstKey = SearchableSettings.HighlightKey(title, isHeading)
        val occurrence = occurrences.getOrDefault(firstKey, 0)
        occurrences[firstKey] = occurrence + 1
        return firstKey.copy(occurrence = occurrence)
    }

    var row = 0
    forEach { preference ->
        when (preference) {
            is Preference.PreferenceGroup -> {
                if (!preference.visible) return@forEach
                action(preference, keyOf(preference.title, isHeading = true), row++)
                preference.preferenceItems.forEach { item ->
                    action(item, keyOf(item.title, isHeading = false), row++)
                }
                row++ // Spacer
            }
            is Preference.PreferenceItem<*, *> -> {
                action(preference, keyOf(preference.title, isHeading = false), row++)
            }
        }
    }
}

/**
 * Returns the heading or item identified by [highlightKey] with its index in the list, or null if there is none
 */
internal fun List<Preference>.findHighlighted(
    highlightKey: SearchableSettings.HighlightKey,
): IndexedValue<Preference>? {
    var highlighted: IndexedValue<Preference>? = null
    forEachRow { preference, key, row ->
        if (highlighted == null && key == highlightKey) highlighted = IndexedValue(row, preference)
    }
    return highlighted
}
