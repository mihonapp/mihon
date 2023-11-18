package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.localize

/**
 * Returns a string of categories name for settings subtitle
 */

@ReadOnlyComposable
@Composable
fun getCategoriesLabel(
    allCategories: List<Category>,
    included: Set<String>,
    excluded: Set<String>,
): String {
    val context = LocalContext.current

    val includedCategories = included
        .mapNotNull { id -> allCategories.find { it.id == id.toLong() } }
        .sortedBy { it.order }
    val excludedCategories = excluded
        .mapNotNull { id -> allCategories.find { it.id == id.toLong() } }
        .sortedBy { it.order }
    val allExcluded = excludedCategories.size == allCategories.size

    val includedItemsText = when {
        // Some selected, but not all
        includedCategories.isNotEmpty() && includedCategories.size != allCategories.size ->
            includedCategories.joinToString { it.visualName(context) }
        // All explicitly selected
        includedCategories.size == allCategories.size -> localize(MR.strings.all)
        allExcluded -> localize(MR.strings.none)
        else -> localize(MR.strings.all)
    }
    val excludedItemsText = when {
        excludedCategories.isEmpty() -> localize(MR.strings.none)
        allExcluded -> localize(MR.strings.all)
        else -> excludedCategories.joinToString { it.visualName(context) }
    }
    return localize(MR.strings.include, includedItemsText) + "\n" +
        localize(MR.strings.exclude, excludedItemsText)
}
