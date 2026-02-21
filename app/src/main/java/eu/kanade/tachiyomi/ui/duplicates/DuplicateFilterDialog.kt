package eu.kanade.tachiyomi.ui.duplicates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.TabbedDialogPaddings
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun DuplicateFilterDialog(
    onDismissRequest: () -> Unit,
    screenModel: PossibleDuplicatesScreenModel,
) {
    val duplicateMatchLevel = screenModel.state.collectAsState().value.matchLevel

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = TabbedDialogPaddings.Horizontal,
                    vertical = TabbedDialogPaddings.Vertical,
                )
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Row {
                Text(
                    text = stringResource(MR.strings.duplicate_match_level_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(top = MaterialTheme.padding.small),
                )
            }
            HorizontalDivider()
            Column {
                listOf(
                    MR.strings.pref_duplicate_automatic_match_exact
                        to LibraryPreferences.DuplicateMatchLevel.ExactMatch,
                    MR.strings.pref_duplicate_automatic_match_fuzzy_title
                        to LibraryPreferences.DuplicateMatchLevel.FuzzyTitle,
                    MR.strings.pref_duplicate_automatic_match_title_substring
                        to LibraryPreferences.DuplicateMatchLevel.TitleSubstring,
                ).map { (titleRes, level) ->
                    RadioItem(
                        label = stringResource(titleRes),
                        selected = duplicateMatchLevel == level,
                        onClick = { screenModel.setMatchLevel(level) },
                    )
                }
            }
        }
    }
}
