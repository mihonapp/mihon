package mihon.desktop.ui.upcoming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.category.DesktopCategory
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.library.TriStateFilter

const val UPCOMING_FILTER_DIALOG_TEST_TAG = "upcoming_filter_dialog"
const val UPCOMING_FILTER_DONE_TEST_TAG = "upcoming_filter_done"
const val UPCOMING_FILTER_CLEAR_TEST_TAG = "upcoming_filter_clear"
const val UPCOMING_FILTER_CATEGORY_TEST_TAG_PREFIX = "upcoming_filter_category_"

@Composable
fun UpcomingFilterDialog(
    categories: List<DesktopCategory>,
    filters: Map<Long, TriStateFilter>,
    onCycleCategory: (Long) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val hasActiveFilters = filters.values.any { it != TriStateFilter.Disabled }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UPCOMING_FILTER_DIALOG_TEST_TAG),
        title = {
            Text(
                text = strings.text(UiText.CategoryFilters),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.text(UiText.CategoryCycleHint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (categories.isEmpty()) {
                    Text(
                        text = strings.text(UiText.NoCategories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEach { category ->
                            UpcomingCategoryFilterRow(
                                category = category,
                                state = filters[category.id] ?: TriStateFilter.Disabled,
                                onClick = { onCycleCategory(category.id) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onClearFilters,
                    enabled = hasActiveFilters,
                    modifier = Modifier.align(Alignment.End).testTag(UPCOMING_FILTER_CLEAR_TEST_TAG),
                ) {
                    Text(strings.text(UiText.ClearFilters))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(UPCOMING_FILTER_DONE_TEST_TAG),
            ) {
                Text(strings.libraryBatchDone)
            }
        },
    )
}

@Composable
private fun UpcomingCategoryFilterRow(
    category: DesktopCategory,
    state: TriStateFilter,
    onClick: () -> Unit,
) {
    val (backgroundColor, contentColor, symbol) = when (state) {
        TriStateFilter.Disabled -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "—",
        )
        TriStateFilter.Include -> Triple(
            Color(0xFF2E7D32),
            Color.White,
            "✓",
        )
        TriStateFilter.Exclude -> Triple(
            Color(0xFFC62828),
            Color.White,
            "✕",
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(UPCOMING_FILTER_CATEGORY_TEST_TAG_PREFIX + category.id),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}
