package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.domain.category.model.Category
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.R

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
        includedCategories.isNotEmpty() && includedCategories.size != allCategories.size -> includedCategories.joinToString { it.visualName(context) }
        // All explicitly selected
        includedCategories.size == allCategories.size -> stringResource(R.string.all)
        allExcluded -> stringResource(R.string.none)
        else -> stringResource(R.string.all)
    }
    val excludedItemsText = when {
        excludedCategories.isEmpty() -> stringResource(R.string.none)
        allExcluded -> stringResource(R.string.all)
        else -> excludedCategories.joinToString { it.visualName(context) }
    }
    return stringResource(R.string.include, includedItemsText) + "\n" +
        stringResource(R.string.exclude, excludedItemsText)
}
