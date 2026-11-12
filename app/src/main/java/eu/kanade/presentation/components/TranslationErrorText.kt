package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.data.translation.TranslationError
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationError.message(detail: String?): String = when (this) {
    TranslationError.PROVIDER_ERROR -> stringResource(
        MR.strings.translation_error_provider,
        detail.orEmpty().replace("%", "%%"),
    )
    TranslationError.MISSING_API_KEY -> stringResource(MR.strings.translation_error_missing_api_key)
    TranslationError.UNSUPPORTED_SOURCE_SCRIPT -> stringResource(MR.strings.translation_error_unsupported_source)
    TranslationError.MISSING_TARGET_LANGUAGE -> stringResource(MR.strings.translation_error_missing_target)
    TranslationError.NO_TEXT_FOUND -> stringResource(MR.strings.translation_error_no_text)
    TranslationError.PAGE_NOT_AVAILABLE -> stringResource(MR.strings.translation_error_page_not_available)
}
