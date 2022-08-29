package eu.kanade.tachiyomi.ui.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import eu.kanade.presentation.more.settings.SettingsMainScreen
import eu.kanade.presentation.more.settings.SettingsSection
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.controller.BasicFullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.setting.search.SettingsSearchController

class SettingsMainController : BasicFullComposeController() {

    @Composable
    override fun ComposeContent() {
        val settingsSections = listOf(
            SettingsSection(
                titleRes = R.string.pref_category_general,
                painter = rememberVectorPainter(Icons.Outlined.Tune),
                onClick = { router.pushController(SettingsGeneralController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_appearance,
                painter = rememberVectorPainter(Icons.Outlined.Palette),
                onClick = { router.pushController(SettingsAppearanceController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_library,
                painter = painterResource(R.drawable.ic_library_outline_24dp),
                onClick = { router.pushController(SettingsLibraryController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_reader,
                painter = rememberVectorPainter(Icons.Outlined.ChromeReaderMode),
                onClick = { router.pushController(SettingsReaderController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_downloads,
                painter = rememberVectorPainter(Icons.Outlined.GetApp),
                onClick = { router.pushController(SettingsDownloadController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_tracking,
                painter = rememberVectorPainter(Icons.Outlined.Sync),
                onClick = { router.pushController(SettingsTrackingController()) },
            ),
            SettingsSection(
                titleRes = R.string.browse,
                painter = painterResource(R.drawable.ic_browse_outline_24dp),
                onClick = { router.pushController(SettingsBrowseController()) },
            ),
            SettingsSection(
                titleRes = R.string.label_backup,
                painter = rememberVectorPainter(Icons.Outlined.SettingsBackupRestore),
                onClick = { router.pushController(SettingsBackupController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_security,
                painter = rememberVectorPainter(Icons.Outlined.Security),
                onClick = { router.pushController(SettingsSecurityController()) },
            ),
            SettingsSection(
                titleRes = R.string.pref_category_advanced,
                painter = rememberVectorPainter(Icons.Outlined.Code),
                onClick = { router.pushController(SettingsAdvancedController()) },
            ),
        )

        SettingsMainScreen(
            navigateUp = router::popCurrentController,
            sections = settingsSections,
            onClickSearch = { router.pushController(SettingsSearchController()) },
        )
    }
}
