package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.gorse.GorsePreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsGorseScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking // 临时使用，显示"Gorse 推荐"

    @Composable
    override fun getPreferences(): List<Preference> {
        val gorsePreferences = remember { Injekt.get<GorsePreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = gorsePreferences.gorseEnabled(),
                title = "启用 Gorse 推荐系统",
                subtitle = "开启后将自动同步阅读记录和获取推荐",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = gorsePreferences.gorseServerUrl(),
                title = "Gorse 服务器地址",
                onValueChanged = {
                    it.isNotBlank()
                },
            ),
            Preference.PreferenceItem.InfoPreference(
                title = "关于 Gorse",
            ),
            Preference.PreferenceItem.TextPreference(
                title = "功能说明",
                subtitle = "• 自动同步阅读记录\n" +
                    "• 手动标记喜欢/不喜欢\n" +
                    "• 个性化推荐\n" +
                    "• 相似漫画推荐",
            ),
        )
    }
}
