package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Environment
import android.preference.PreferenceManager
import com.f2prateek.rx.preferences.Preference
import com.f2prateek.rx.preferences.RxSharedPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.mangasync.base.MangaSyncService
import eu.kanade.tachiyomi.data.source.base.Source
import java.io.File
import java.io.IOException

fun <T> Preference<T>.getOrDefault(): T = get() ?: defaultValue()!!

class PreferencesHelper(private val context: Context) {

    val keys = PreferenceKeys(context)

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val rxPrefs = RxSharedPreferences.create(prefs)

    private val defaultDownloadsDir = File(Environment.getExternalStorageDirectory().absolutePath +
            File.separator + context.getString(R.string.app_name), "downloads")

    init {
        // Don't display downloaded chapters in gallery apps creating a ".nomedia" file
        try {
            File(downloadsDirectory().getOrDefault(), ".nomedia").createNewFile()
        } catch (e: IOException) {
            /* Ignore */
        }
    }

    companion object {

        fun getLibraryUpdateInterval(context: Context): Int {
            return PreferenceManager.getDefaultSharedPreferences(context).getInt(
                    context.getString(R.string.pref_library_update_interval_key), 0)
        }

        @JvmStatic
        fun getTheme(context: Context): Int {
            return PreferenceManager.getDefaultSharedPreferences(context).getInt(
                    context.getString(R.string.pref_theme_key), 1)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun rotation() = rxPrefs.getInteger(keys.rotation, 1)

    fun enableTransitions() = rxPrefs.getBoolean(keys.enableTransitions, true)

    fun showPageNumber() = rxPrefs.getBoolean(keys.showPageNumber, true)

    fun hideStatusBar() = rxPrefs.getBoolean(keys.hideStatusBar, true)

    fun keepScreenOn() = rxPrefs.getBoolean(keys.keepScreenOn, true)

    fun customBrightness() = rxPrefs.getBoolean(keys.customBrightness, false)

    fun customBrightnessValue() = rxPrefs.getFloat(keys.customBrightnessValue, 0f)

    fun defaultViewer() = prefs.getInt(keys.defaultViewer, 1)

    fun imageScaleType() = rxPrefs.getInteger(keys.imageScaleType, 1)

    fun imageDecoder() = rxPrefs.getInteger(keys.imageDecoder, 0)

    fun zoomStart() = rxPrefs.getInteger(keys.zoomStart, 1)

    fun readerTheme() = rxPrefs.getInteger(keys.readerTheme, 0)

    fun readWithTapping() = rxPrefs.getBoolean(keys.readWithTapping, true)

    fun readWithVolumeKeys() = rxPrefs.getBoolean(keys.readWithVolumeKeys, false)

    fun portraitColumns() = rxPrefs.getInteger(keys.portraitColumns, 0)

    fun landscapeColumns() = rxPrefs.getInteger(keys.landscapeColumns, 0)

    fun updateOnlyNonCompleted() = prefs.getBoolean(keys.updateOnlyNonCompleted, false)

    fun autoUpdateMangaSync() = prefs.getBoolean(keys.autoUpdateMangaSync, true)

    fun askUpdateMangaSync() = prefs.getBoolean(keys.askUpdateMangaSync, false)

    fun lastUsedCatalogueSource() = rxPrefs.getInteger(keys.lastUsedCatalogueSource, -1)

    fun seamlessMode() = prefs.getBoolean(keys.seamlessMode, true)

    fun catalogueAsList() = rxPrefs.getBoolean(keys.catalogueAsList, false)

    fun enabledLanguages() = rxPrefs.getStringSet(keys.enabledLanguages, setOf("EN"))

    fun sourceUsername(source: Source) = prefs.getString(keys.sourceUsername(source.id), "")

    fun sourcePassword(source: Source) = prefs.getString(keys.sourcePassword(source.id), "")

    fun setSourceCredentials(source: Source, username: String, password: String) {
        prefs.edit()
                .putString(keys.sourceUsername(source.id), username)
                .putString(keys.sourcePassword(source.id), password)
                .apply()
    }

    fun mangaSyncUsername(sync: MangaSyncService) = prefs.getString(keys.syncUsername(sync.id), "")

    fun mangaSyncPassword(sync: MangaSyncService) = prefs.getString(keys.syncPassword(sync.id), "")

    fun setMangaSyncCredentials(sync: MangaSyncService, username: String, password: String) {
        prefs.edit()
                .putString(keys.syncUsername(sync.id), username)
                .putString(keys.syncPassword(sync.id), password)
                .apply()
    }

    fun downloadsDirectory() = rxPrefs.getString(keys.downloadsDirectory, defaultDownloadsDir.absolutePath)

    fun downloadThreads() = rxPrefs.getInteger(keys.downloadThreads, 1)

    fun downloadOnlyOverWifi() = prefs.getBoolean(keys.downloadOnlyOverWifi, true)

    fun removeAfterRead() = prefs.getBoolean(keys.removeAfterRead, false)

    fun removeAfterReadPrevious() = prefs.getBoolean(keys.removeAfterReadPrevious, false)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(keys.removeAfterMarkedAsRead, false)

    fun updateOnlyWhenCharging() = prefs.getBoolean(keys.updateOnlyWhenCharging, false)

    fun libraryUpdateInterval() = rxPrefs.getInteger(keys.libraryUpdateInterval, 0)

    fun filterDownloaded() = rxPrefs.getBoolean(keys.filterDownloaded, false)

    fun filterUnread() = rxPrefs.getBoolean(keys.filterUnread, false)

}
