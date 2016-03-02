package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import com.nononsenseapps.filepicker.AbstractFilePickerFragment
import com.nononsenseapps.filepicker.FilePickerActivity
import com.nononsenseapps.filepicker.FilePickerFragment
import com.nononsenseapps.filepicker.LogicHandler
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.inflate
import java.io.File

class SettingsDownloadsFragment : SettingsNestedFragment() {

    val downloadDirPref by lazy { findPreference(getString(R.string.pref_download_directory_key)) }

    companion object {

        val DOWNLOAD_DIR_CODE = 103

        fun newInstance(resourcePreference: Int, resourceTitle: Int): SettingsNestedFragment {
            val fragment = SettingsDownloadsFragment()
            fragment.setArgs(resourcePreference, resourceTitle)
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedState: Bundle?) {
        downloadDirPref.setOnPreferenceClickListener {

            val externalDirs = getExternalFilesDirs()
            val selectedIndex = externalDirs.indexOf(File(preferences.downloadsDirectory))

            MaterialDialog.Builder(activity)
                    .items(externalDirs + getString(R.string.custom_dir))
                    .itemsCallbackSingleChoice(selectedIndex, { dialog, view, which, text ->
                        if (which == externalDirs.size) {
                            // Custom dir selected, open directory selector
                            val i = Intent(activity, CustomLayoutPickerActivity::class.java)
                            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
                            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
                            i.putExtra(FilePickerActivity.EXTRA_START_PATH, preferences.downloadsDirectory)

                            startActivityForResult(i, DOWNLOAD_DIR_CODE)
                        } else {
                            // One of the predefined folders was selected
                            preferences.downloadsDirectory = text.toString()
                            updateDownloadsDir()
                        }
                        true
                    })
                    .show()

            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateDownloadsDir()
    }

    fun updateDownloadsDir() {
        downloadDirPref.summary = preferences.downloadsDirectory
    }

    fun getExternalFilesDirs(): List<File> {
        val defaultDir = Environment.getExternalStorageDirectory().absolutePath +
                File.separator + getString(R.string.app_name) +
                File.separator + "downloads"

        return mutableListOf(File(defaultDir)) + activity.getExternalFilesDirs("")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null && requestCode == DOWNLOAD_DIR_CODE && resultCode == Activity.RESULT_OK) {
            preferences.downloadsDirectory = data.data.path
        }
    }

    class CustomLayoutPickerActivity : FilePickerActivity() {

        override fun getFragment(startPath: String?, mode: Int, allowMultiple: Boolean, allowCreateDir: Boolean):
                AbstractFilePickerFragment<File> {
            val fragment = CustomLayoutFilePickerFragment()
            fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir)
            return fragment
        }
    }

    class CustomLayoutFilePickerFragment : FilePickerFragment() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            when (viewType) {
                LogicHandler.VIEWTYPE_DIR -> {
                    val view = parent.inflate(R.layout.listitem_dir)
                    return DirViewHolder(view)
                }
                else -> return super.onCreateViewHolder(parent, viewType)
            }
        }
    }

}
