package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.LocaleHelper
import org.xmlpull.v1.XmlPullParser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsGeneralScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_general

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<BasePreferences>() }
        val libraryPrefs = remember { Injekt.get<LibraryPreferences>() }
        return mutableListOf<Preference>().apply {
            add(
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPrefs.showUpdatesNavBadge(),
                    title = stringResource(R.string.pref_library_update_show_tab_badge),
                ),
            )

            add(
                Preference.PreferenceItem.SwitchPreference(
                    pref = prefs.confirmExit(),
                    title = stringResource(R.string.pref_confirm_exit),
                ),
            )

            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(R.string.pref_manage_notifications),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                    ),
                )
            }

            val langs = remember { getLangs(context) }
            var currentLanguage by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "") }
            add(
                Preference.PreferenceItem.BasicListPreference(
                    value = currentLanguage,
                    title = stringResource(R.string.pref_app_language),
                    entries = langs,
                    onValueChanged = { newValue ->
                        currentLanguage = newValue
                        true
                    },
                ),
            )

            LaunchedEffect(currentLanguage) {
                val locale = if (currentLanguage.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(currentLanguage)
                }
                AppCompatDelegate.setApplicationLocales(locale)
            }
        }
    }

    private fun getLangs(context: Context): Map<String, String> {
        val langs = mutableListOf<Pair<String, String>>()
        val parser = context.resources.getXml(R.xml.locales_config)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "name") {
                        val langTag = parser.getAttributeValue(i)
                        val displayName = LocaleHelper.getDisplayName(langTag)
                        if (displayName.isNotEmpty()) {
                            langs.add(Pair(langTag, displayName))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        langs.sortBy { it.second }
        langs.add(0, Pair("", context.getString(R.string.label_default)))

        return langs.toMap()
    }
}
