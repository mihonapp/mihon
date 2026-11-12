package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import tachiyomi.domain.translation.model.TranslationLanguage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@Composable
fun TranslationLanguage.displayName(): String {
    if (this == TranslationLanguage.AUTO) return stringResource(MR.strings.translation_language_auto)
    return Locale.forLanguageTag(code).getDisplayName(Locale.getDefault()).ifBlank { code }
}
