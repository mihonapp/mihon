package eu.kanade.tachiyomi.ui.reader.setting

import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.tachiyomi.util.preference.toggle
import tachiyomi.core.preference.Preference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsScreenModel(
    val preferences: ReaderPreferences = Injekt.get(),
) : ScreenModel {

    fun togglePreference(preference: (ReaderPreferences) -> Preference<Boolean>) {
        preference(preferences).toggle()
    }
}
