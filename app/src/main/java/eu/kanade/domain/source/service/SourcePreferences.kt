package eu.kanade.domain.source.service

import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.util.system.LocaleHelper

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = this.preferenceStore.getObject("pref_display_mode_catalogue", LibraryDisplayMode.default, LibraryDisplayMode.Serializer::serialize, LibraryDisplayMode.Serializer::deserialize)

    fun enabledLanguages() = this.preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = this.preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = this.preferenceStore.getStringSet("pinned_catalogues", emptySet())

    fun duplicatePinnedSources() = this.preferenceStore.getBoolean("duplicate_pinned_sources", false)

    fun lastUsedSource() = this.preferenceStore.getLong("last_catalogue_source", -1)

    fun showNsfwSource() = this.preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = this.preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = this.preferenceStore.getEnum("pref_migration_direction", SetMigrateSorting.Direction.ASCENDING)

    fun extensionUpdatesCount() = this.preferenceStore.getInt("ext_updates_count", 0)

    fun trustedSignatures() = this.preferenceStore.getStringSet("trusted_signatures", emptySet())

    fun searchPinnedSourcesOnly() = this.preferenceStore.getBoolean("search_pinned_sources_only", false)
}
