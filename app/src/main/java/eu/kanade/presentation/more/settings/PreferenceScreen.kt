package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.presentation.more.settings.screen.PreferenceHighlightKey
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
    if (highlightKey != null) {
        LaunchedEffect(Unit) {
            val i = items.findHighlightedIndex(highlightKey)
            if (i >= 0) {
                delay(0.5.seconds)
                state.animateScrollToItem(i)
            }
            SearchableSettings.highlightKey = null
        }
    }

    ScrollbarLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        val headerOccurrences = mutableMapOf<String, Int>()
        val itemOccurrences = mutableMapOf<String, Int>()

        items.fastForEachIndexed { i, preference ->
            when (preference) {
                // Create Preference Group
                is Preference.PreferenceGroup -> {
                    if (!preference.visible) return@fastForEachIndexed

                    val headerOccurrence = headerOccurrences.nextOccurrence(preference.title)
                    val headerHighlighted = highlightKey?.let {
                        it.isGroupHeader && it.title == preference.title && it.occurrence == headerOccurrence
                    } == true
                    item {
                        Column {
                            PreferenceGroupHeader(
                                title = preference.title,
                                highlighted = headerHighlighted,
                            )
                        }
                    }
                    preference.preferenceItems.fastForEach { item ->
                        val occurrence = if (item.visible) itemOccurrences.nextOccurrence(item.title) else -1
                        val highlighted = highlightKey?.let {
                            !it.isGroupHeader && it.title == item.title && it.occurrence == occurrence
                        } == true
                        item {
                            PreferenceItem(
                                item = item,
                                highlighted = highlighted,
                            )
                        }
                    }
                    item {
                        if (i < items.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // Create Preference Item
                is Preference.PreferenceItem<*, *> -> {
                    val occurrence = if (preference.visible) itemOccurrences.nextOccurrence(preference.title) else -1
                    val highlighted = highlightKey?.let {
                        !it.isGroupHeader && it.title == preference.title && it.occurrence == occurrence
                    } == true
                    item {
                        PreferenceItem(
                            item = preference,
                            highlighted = highlighted,
                        )
                    }
                }
            }
        }
    }
}

private fun MutableMap<String, Int>.nextOccurrence(title: String): Int {
    val occurrence = this[title] ?: 0
    this[title] = occurrence + 1
    return occurrence
}

private fun List<Preference>.findHighlightedIndex(highlightKey: PreferenceHighlightKey): Int {
    val headerOccurrences = mutableMapOf<String, Int>()
    val itemOccurrences = mutableMapOf<String, Int>()
    var index = 0

    forEachIndexed { i, preference ->
        when (preference) {
            is Preference.PreferenceGroup -> {
                if (!preference.visible) return@forEachIndexed

                val headerOccurrence = headerOccurrences.nextOccurrence(preference.title)
                if (
                    highlightKey.isGroupHeader &&
                    highlightKey.title == preference.title &&
                    highlightKey.occurrence == headerOccurrence
                ) {
                    return index
                }
                index++ // Header

                preference.preferenceItems.fastForEach { item ->
                    if (item.visible) {
                        val itemOccurrence = itemOccurrences.nextOccurrence(item.title)
                        if (
                            !highlightKey.isGroupHeader &&
                            highlightKey.title == item.title &&
                            highlightKey.occurrence == itemOccurrence
                        ) {
                            return index
                        }
                    }
                    index++
                }
                if (i < lastIndex) index++ // Spacer
            }
            is Preference.PreferenceItem<*, *> -> {
                if (preference.visible) {
                    val itemOccurrence = itemOccurrences.nextOccurrence(preference.title)
                    if (
                        !highlightKey.isGroupHeader &&
                        highlightKey.title == preference.title &&
                        highlightKey.occurrence == itemOccurrence
                    ) {
                        return index
                    }
                }
                index++
            }
        }
    }

    return -1
}
