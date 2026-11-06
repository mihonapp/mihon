package tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LazyItemScope.SectionCard(
    titleRes: StringResource? = null,
    // Mekuri: Statistics centres its section titles
    centerTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (titleRes != null) {
        Text(
            modifier = Modifier
                .then(if (centerTitle) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = MaterialTheme.padding.extraLarge),
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            textAlign = if (centerTitle) TextAlign.Center else null,
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            content()
        }
    }
}
