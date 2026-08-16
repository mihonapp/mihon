package eu.kanade.domain.source.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.extension.model.ContentWarning
import tachiyomi.i18n.MR

/**
 * User-selectable cutoff for which [ContentWarning] tiers are surfaced when discovering new
 * extensions/sources in Browse, and for which already-installed ones stay listed — see
 * `GetExtensionsByType`.
 *
 * Entries are declared least to most permissive; [allowsDiscovery] treats that ordering as
 * cumulative.
 */
enum class ContentWarningLevel(val titleRes: StringResource) {
    SAFE(MR.strings.content_warning_level_safe),
    SAFE_AND_MIXED(MR.strings.content_warning_level_safe_and_mixed),
    ALL(MR.strings.content_warning_level_all),
    ;

    /** Whether a not-yet-installed extension/source of this [contentWarning] shows up in Browse. */
    fun allowsDiscovery(contentWarning: ContentWarning): Boolean {
        return when (this) {
            SAFE -> !contentWarning.hasAdultContent
            SAFE_AND_MIXED -> contentWarning != ContentWarning.NSFW
            ALL -> true
        }
    }

    /**
     * Whether an already-installed extension of this [contentWarning] stays listed.
     *
     * Only the strictest level, [SAFE], also hides installed extensions — [SAFE_AND_MIXED] and
     * [ALL] never hide something the user explicitly installed, so they keep receiving updates
     * and don't regress the fix for https://github.com/mihonapp/mihon/issues/1673.
     */
    fun allowsInstalled(contentWarning: ContentWarning): Boolean {
        return this != SAFE || !contentWarning.hasAdultContent
    }
}
