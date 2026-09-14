package eu.kanade.presentation.browse.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.StringResource
import mihon.domain.extension.model.ContentWarning
import tachiyomi.i18n.MR

internal data class ContentWarningLabel(
    val title: StringResource,
    val description: StringResource,
    val color: Color,
)

internal val ContentWarning.label: ContentWarningLabel?
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ContentWarning.SAFE -> null
        ContentWarning.MIXED -> ContentWarningLabel(
            title = MR.strings.ext_content_warning_mixed,
            description = MR.strings.ext_content_warning_mixed_description,
            color = MaterialTheme.colorScheme.primary,
        )
        ContentWarning.NSFW -> ContentWarningLabel(
            title = MR.strings.ext_content_warning_nsfw,
            description = MR.strings.ext_content_warning_nsfw_description,
            color = MaterialTheme.colorScheme.error,
        )
    }
