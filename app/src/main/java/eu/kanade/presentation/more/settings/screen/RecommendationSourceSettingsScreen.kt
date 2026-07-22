package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.recommendation.service.RecommendationPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

object RecommendationSourceSettingsScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_source_recommendation_settings

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val recommendationPreferences = remember { Injekt.get<RecommendationPreferences>() }
        val sources by sourceManager.sources.collectAsState(initial = emptyList())

        val onlineSources = remember(sources, context) {
            sources
                .filterIsInstance<HttpSource>()
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<HttpSource> { source -> source.localizedLanguage(context).lowercase(Locale.ROOT) }
                        .thenBy { source -> source.name.lowercase(Locale.ROOT) },
                )
        }

        if (onlineSources.isEmpty()) {
            return listOf(
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_source_recommendation_no_sources),
                ),
            )
        }

        return buildList {
            add(
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(MR.strings.pref_source_recommendation_info),
                ),
            )
            onlineSources
                .groupBy(HttpSource::lang)
                .forEach { (_, languageSources) ->
                    add(
                        Preference.PreferenceGroup(
                            title = languageSources.first().localizedLanguage(context),
                            preferenceItems = languageSources.map { source ->
                                Preference.PreferenceItem.SwitchPreference(
                                    preference = recommendationPreferences.networkEnabled(source.id),
                                    title = source.name,
                                )
                            },
                        ),
                    )
                }
        }
    }
}

private fun HttpSource.localizedLanguage(context: Context): String {
    return LocaleHelper.getSourceDisplayName(lang, context)
}
