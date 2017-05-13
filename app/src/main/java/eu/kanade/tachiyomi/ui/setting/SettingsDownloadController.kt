package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.support.v7.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.hippo.unifile.UniFile
import com.nononsenseapps.filepicker.FilePickerActivity
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.util.DiskUtil
import eu.kanade.tachiyomi.widget.CustomLayoutPickerActivity
import uy.kohesive.injekt.injectLazy
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsDownloadController : SettingsController() {

    private val db: DatabaseHelper by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_downloads

        preference {
            key = Keys.downloadsDirectory
            titleRes = R.string.pref_download_directory
            onClick {
                showDownloadDirectoriesDialog()
            }

            preferences.downloadsDirectory().asObservable()
                    .subscribeUntilDestroy { path ->
                        val dir = UniFile.fromUri(context, Uri.parse(path))
                        summary = dir.filePath ?: path

                        // Don't display downloaded chapters in gallery apps creating .nomedia
                        if (dir != null && dir.exists()) {
                            val nomedia = dir.findFile(".nomedia")
                            if (nomedia == null) {
                                dir.createFile(".nomedia")
                                applicationContext?.let { DiskUtil.scanMedia(it, dir.uri) }
                            }
                        }
                    }
        }
        switchPreference {
            key = Keys.downloadOnlyOverWifi
            titleRes = R.string.pref_download_only_over_wifi
            defaultValue = true
        }
        intListPreference {
            key = Keys.downloadThreads
            titleRes = R.string.pref_download_slots
            entries = arrayOf("1", "2", "3")
            entryValues = arrayOf("1", "2", "3")
            defaultValue = "1"
            summary = "%s"
        }
        preferenceCategory {
            titleRes = R.string.pref_remove_after_read

            switchPreference {
                key = Keys.removeAfterMarkedAsRead
                titleRes = R.string.pref_remove_after_marked_as_read
                defaultValue = false
            }
            intListPreference {
                key = Keys.removeAfterReadSlots
                titleRes = R.string.pref_remove_after_read
                entriesRes = arrayOf(R.string.disabled, R.string.last_read_chapter,
                        R.string.second_to_last, R.string.third_to_last, R.string.fourth_to_last,
                        R.string.fifth_to_last)
                entryValues = arrayOf("-1", "0", "1", "2", "3", "4")
                defaultValue = "-1"
                summary = "%s"
            }
        }

        val dbCategories = db.getCategories().executeAsBlocking()

        preferenceCategory {
            titleRes = R.string.pref_download_new

            switchPreference {
                key = Keys.downloadNew
                titleRes = R.string.pref_download_new
                defaultValue = false
            }
            multiSelectListPreference {
                key = Keys.downloadNewCategories
                titleRes = R.string.pref_download_new_categories
                entries = dbCategories.map { it.name }.toTypedArray()
                entryValues = dbCategories.map { it.id.toString() }.toTypedArray()

                preferences.downloadNew().asObservable()
                        .subscribeUntilDestroy { isVisible = it }

                preferences.downloadNewCategories().asObservable()
                        .subscribe {
                            val selectedCategories = it
                                    .mapNotNull { id -> dbCategories.find { it.id == id.toInt() } }
                                    .sortedBy { it.order }

                            summary = if (selectedCategories.isEmpty())
                                resources?.getString(R.string.all)
                            else
                                selectedCategories.joinToString { it.name }
                        }
            }
        }
    }

    private fun showDownloadDirectoriesDialog() {
        val activity = activity ?: return

        val currentDir = preferences.downloadsDirectory().getOrDefault()
        val externalDirs = getExternalFilesDirs() + File(activity.getString(R.string.custom_dir))
        val selectedIndex = externalDirs.map(File::toString).indexOfFirst { it in currentDir }

        MaterialDialog.Builder(activity)
                .items(externalDirs)
                .itemsCallbackSingleChoice(selectedIndex, { _, _, which, text ->
                    if (which == externalDirs.lastIndex) {
                        if (Build.VERSION.SDK_INT < 21) {
                            // Custom dir selected, open directory selector
                            val i = Intent(activity, CustomLayoutPickerActivity::class.java)
                            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
                            i.putExtra(FilePickerActivity.EXTRA_START_PATH, currentDir)

                            startActivityForResult(i, DOWNLOAD_DIR_PRE_L)
                        } else {
                            val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            startActivityForResult(i, DOWNLOAD_DIR_L)
                        }
                    } else {
                        // One of the predefined folders was selected
                        val path = Uri.fromFile(File(text.toString()))
                        preferences.downloadsDirectory().set(path.toString())
                    }
                    true
                })
                .show()
    }

    private fun getExternalFilesDirs(): List<File> {
        val defaultDir = Environment.getExternalStorageDirectory().absolutePath +
                File.separator + resources?.getString(R.string.app_name) +
                File.separator + "downloads"

        return mutableListOf(File(defaultDir)) +
                ContextCompat.getExternalFilesDirs(activity, "").filterNotNull()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DOWNLOAD_DIR_PRE_L -> if (data != null && resultCode == Activity.RESULT_OK) {
                val uri = Uri.fromFile(File(data.data.path))
                preferences.downloadsDirectory().set(uri.toString())
            }
            DOWNLOAD_DIR_L -> if (data != null && resultCode == Activity.RESULT_OK) {
                val context = applicationContext ?: return
                val uri = data.data
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                @Suppress("NewApi")
                context.contentResolver.takePersistableUriPermission(uri, flags)

                val file = UniFile.fromUri(context, uri)
                preferences.downloadsDirectory().set(file.uri.toString())
            }
        }
    }

    private companion object {
        const val DOWNLOAD_DIR_PRE_L = 103
        const val DOWNLOAD_DIR_L = 104
    }
}