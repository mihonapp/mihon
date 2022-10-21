package eu.kanade.presentation.more.settings.screen

import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    @StringRes
    override fun getTitleRes() = R.string.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = runBlocking { getCategories.await() })

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        return listOf(
            getDownloadLocationPreference(downloadPreferences = downloadPreferences),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(R.string.connected_to_wifi),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(R.string.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.splitTallImages(),
                title = stringResource(R.string.split_tall_images),
                subtitle = stringResource(R.string.split_tall_images_summary),
            ),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
        )
    }

    @Composable
    private fun getDownloadLocationPreference(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceItem.ListPreference<String> {
        val context = LocalContext.current
        val currentDirPref = downloadPreferences.downloadsDirectory()
        val currentDir by currentDirPref.collectAsState()

        val pickLocation = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(context, uri)
                currentDirPref.set(file.uri.toString())
            }
        }

        val defaultDirPair = rememberDefaultDownloadDir()
        val customDirEntryKey = currentDir.takeIf { it != defaultDirPair.first } ?: "custom"

        return Preference.PreferenceItem.ListPreference(
            pref = currentDirPref,
            title = stringResource(R.string.pref_download_directory),
            subtitle = remember(currentDir) {
                UniFile.fromUri(context, currentDir.toUri())?.filePath
            } ?: stringResource(R.string.invalid_location, currentDir),
            entries = mapOf(
                defaultDirPair,
                customDirEntryKey to stringResource(R.string.custom_dir),
            ),
            onValueChanged = {
                val default = it == defaultDirPair.first
                if (!default) {
                    pickLocation.launch(null)
                }
                default // Don't update when non-default chosen
            },
        )
    }

    @Composable
    private fun rememberDefaultDownloadDir(): Pair<String, String> {
        val appName = stringResource(R.string.app_name)
        return remember {
            val file = UniFile.fromFile(
                File(
                    "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}$appName",
                    "downloads",
                ),
            )!!
            file.uri.toString() to file.filePath!!
        }
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_delete_chapters),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(R.string.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.removeAfterReadSlots(),
                    title = stringResource(R.string.pref_remove_after_read),
                    entries = mapOf(
                        -1 to stringResource(R.string.disabled),
                        0 to stringResource(R.string.last_read_chapter),
                        1 to stringResource(R.string.second_to_last),
                        2 to stringResource(R.string.third_to_last),
                        3 to stringResource(R.string.fourth_to_last),
                        4 to stringResource(R.string.fifth_to_last),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(R.string.pref_remove_bookmarked_chapters),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        val none = stringResource(R.string.none)
        val pref = downloadPreferences.removeExcludeCategories()
        val entries = categories().associate { it.id.toString() to it.visualName }
        val subtitle by produceState(initialValue = "") {
            pref.changes()
                .stateIn(this)
                .collect { mutable ->
                    value = mutable
                        .mapNotNull { id -> entries[id] }
                        .sortedBy { entries.values.indexOf(it) }
                        .joinToString()
                        .ifEmpty { none }
                }
        }
        return Preference.PreferenceItem.MultiSelectListPreference(
            pref = pref,
            title = stringResource(R.string.pref_remove_exclude_categories),
            subtitle = subtitle,
            entries = entries,
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(R.string.categories),
                message = stringResource(R.string.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.map { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.map { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_auto_download),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewChaptersPref,
                    title = stringResource(R.string.pref_download_new),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showDialog = true },
                    enabled = downloadNewChapters,
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(R.string.download_ahead),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.autoDownloadWhileReading(),
                    title = stringResource(R.string.auto_download_while_reading),
                    entries = listOf(0, 2, 3, 5, 10).associateWith {
                        if (it == 0) {
                            stringResource(R.string.disabled)
                        } else {
                            pluralStringResource(id = R.plurals.next_unread_chapters, count = it, it)
                        }
                    },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(R.string.download_ahead_info)),
            ),
        )
    }
}
