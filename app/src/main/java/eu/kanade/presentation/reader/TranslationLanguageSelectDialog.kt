package eu.kanade.presentation.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Data class representing a translation language option.
 */
data class TranslationLanguage(
    val code: String,
    val displayName: String,
)

/**
 * List of available translation target languages.
 */
val translationLanguages = listOf(
    TranslationLanguage("en", "English"),
    TranslationLanguage("es", "Spanish"),
    TranslationLanguage("fr", "French"),
    TranslationLanguage("de", "German"),
    TranslationLanguage("it", "Italian"),
    TranslationLanguage("pt", "Portuguese"),
    TranslationLanguage("ru", "Russian"),
    TranslationLanguage("ja", "Japanese"),
    TranslationLanguage("ko", "Korean"),
    TranslationLanguage("zh", "Chinese (Simplified)"),
    TranslationLanguage("zh-TW", "Chinese (Traditional)"),
    TranslationLanguage("ar", "Arabic"),
    TranslationLanguage("hi", "Hindi"),
    TranslationLanguage("th", "Thai"),
    TranslationLanguage("vi", "Vietnamese"),
    TranslationLanguage("id", "Indonesian"),
    TranslationLanguage("ms", "Malay"),
    TranslationLanguage("nl", "Dutch"),
    TranslationLanguage("pl", "Polish"),
    TranslationLanguage("tr", "Turkish"),
    TranslationLanguage("uk", "Ukrainian"),
    TranslationLanguage("cs", "Czech"),
    TranslationLanguage("sv", "Swedish"),
    TranslationLanguage("da", "Danish"),
    TranslationLanguage("fi", "Finnish"),
    TranslationLanguage("no", "Norwegian"),
    TranslationLanguage("el", "Greek"),
    TranslationLanguage("he", "Hebrew"),
    TranslationLanguage("hu", "Hungarian"),
    TranslationLanguage("ro", "Romanian"),
)

@Composable
fun TranslationLanguageSelectDialog(
    onDismissRequest: () -> Unit,
    currentLanguage: String,
    autoTranslateEnabled: Boolean,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        DialogContent(
            currentLanguage = currentLanguage,
            autoTranslateEnabled = autoTranslateEnabled,
            onToggleAutoTranslate = onToggleAutoTranslate,
            onSelectLanguage = { code ->
                onSelectLanguage(code)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun DialogContent(
    currentLanguage: String,
    autoTranslateEnabled: Boolean,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(currentLanguage) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(MR.strings.action_translate),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleAutoTranslate(!autoTranslateEnabled) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Auto-Translate",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Only translate if language differs from target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoTranslateEnabled,
                onCheckedChange = onToggleAutoTranslate,
            )
        }

        Text(
            text = "Select target language for translation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            items(translationLanguages) { language ->
                LanguageItem(
                    language = language,
                    isSelected = selected == language.code,
                    onClick = {
                        selected = language.code
                        onSelectLanguage(language.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageItem(
    language: TranslationLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )

        Spacer(modifier = Modifier.width(MaterialTheme.padding.small))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = language.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
