package eu.kanade.tachiyomi.data.library

import android.content.Context
import com.google.android.gms.gcm.*
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryUpdateTrigger : GcmTaskService() {

    override fun onInitializeTasks() {
        setupTask(this)
    }

    override fun onRunTask(params: TaskParams): Int {
        LibraryUpdateService.start(this)
        return GcmNetworkManager.RESULT_SUCCESS
    }

    companion object {
        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<PreferencesHelper>()
            val interval = prefInterval ?: preferences.libraryUpdateInterval().getOrDefault()
            if (interval > 0) {
                val restrictions = preferences.libraryUpdateRestriction()
                val acRestriction = "ac" in restrictions
                val wifiRestriction = if ("wifi" in restrictions)
                    Task.NETWORK_STATE_UNMETERED
                else
                    Task.NETWORK_STATE_ANY

                val task = PeriodicTask.Builder()
                        .setService(LibraryUpdateTrigger::class.java)
                        .setTag("Library periodic update")
                        .setPeriod(interval * 60 * 60L)
                        .setFlex(5 * 60)
                        .setRequiredNetwork(wifiRestriction)
                        .setRequiresCharging(acRestriction)
                        .setUpdateCurrent(true)
                        .setPersisted(true)
                        .build()

                GcmNetworkManager.getInstance(context).schedule(task)
            }
        }

        fun cancelTask(context: Context) {
            GcmNetworkManager.getInstance(context).cancelAllTasks(LibraryUpdateTrigger::class.java)
        }
    }
}