package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.browse.ExtensionReposScreen
import eu.kanade.tachiyomi.ui.browse.source.blockrule.BlockruleScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extensionrepo.interactor.GetExtensionRepoCount
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.blockrule.interactor.GetBlockrules
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBrowseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.browse

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
        val getExtensionRepoCount = remember { Injekt.get<GetExtensionRepoCount>() }
        val getBlockrules = remember { Injekt.get<GetBlockrules>() }

        val reposCount by getExtensionRepoCount.subscribe().collectAsState(0)
        val allBlockrules by getBlockrules.subscribe().collectAsState(initial = emptyList())

        val prefetchPagesPref = sourcePreferences.prefetchPages()
        val prefetchPages by prefetchPagesPref.collectAsState()

        val pageItemsPref = sourcePreferences.pageItems()
        val pageItems by pageItemsPref.collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.hideInLibraryItems(),
                        title = stringResource(MR.strings.pref_hide_in_library_items),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.block_rules),
                        subtitle = pluralStringResource(
                            MR.plurals.num_blockrules,
                            count = allBlockrules.size,
                            allBlockrules.size
                        ),
                        onClick = { navigator.push(BlockruleScreen()) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.label_extension_repos),
                        subtitle = pluralStringResource(MR.plurals.num_repos, reposCount, reposCount),
                        onClick = {
                            navigator.push(ExtensionReposScreen())
                        },
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = prefetchPages,
                        min = 1,
                        max = 10,
                        title = "预读取页",//i18n
                        subtitle = "页数 $prefetchPages",
                        onValueChanged = {
                            prefetchPagesPref.set(it)
                            true
                        },
                        enabled = true,
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = pageItems,
                        min = 1,
                        max = 10,
                        title = "每页大小",//i18n
                        subtitle = "数量 $pageItems",
                        onValueChanged = {
                            pageItemsPref.set(it)
                            true
                        },
                        enabled = true,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_nsfw_content),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = sourcePreferences.showNsfwSource(),
                        title = stringResource(MR.strings.pref_show_nsfw_source),
                        subtitle = stringResource(MR.strings.requires_app_restart),
                        onValueChanged = {
                            (context as FragmentActivity).authenticate(
                                title = context.stringResource(MR.strings.pref_category_nsfw_content),
                            )
                        },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.parental_controls_info)),
                ),
            ),
        )
    }
}
