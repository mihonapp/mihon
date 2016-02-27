package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadDirPref.setOnPreferenceClickListener { preference ->
            val i = Intent(activity, CustomLayoutPickerActivity::class.java)
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, preferences.downloadsDirectory)

            startActivityForResult(i, DOWNLOAD_DIR_CODE)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        downloadDirPref.summary = preferences.downloadsDirectory
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
