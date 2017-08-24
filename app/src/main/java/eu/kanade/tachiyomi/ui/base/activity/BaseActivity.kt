package eu.kanade.tachiyomi.ui.base.activity

import android.support.v7.app.AppCompatActivity
import eu.kanade.tachiyomi.util.LocaleHelper

abstract class BaseActivity : AppCompatActivity() {

    init {
        @Suppress("LeakingThis")
        LocaleHelper.updateConfiguration(this)
    }

    var willLock = false
    var disableLock = false
    override fun onRestart() {
        super.onRestart()
        if(willLock && lockEnabled() && !disableLock) {
            showLockActivity(this)
        }

        willLock = false
    }

    override fun onStop() {
        super.onStop()
        tryLock()
    }

    fun tryLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mUsageStatsManager = getSystemService("usagestats") as UsageStatsManager
            val time = System.currentTimeMillis()
            // We get usage stats for the last 20 seconds
            val stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 20, time)
            // Sort the stats by the last time used
            if (stats != null) {
                val mySortedMap = TreeMap<Long, UsageStats>()
                for (usageStats in stats) {
                    mySortedMap.put(usageStats.lastTimeUsed, usageStats)
                }
                if (!mySortedMap.isEmpty()) {
                    if(mySortedMap[mySortedMap.lastKey()]?.packageName != packageName) {
                        willLock = true
                    }
                }
            }
        } else {
            val am = getSystemService(Service.ACTIVITY_SERVICE) as ActivityManager
            val tasks: List<ActivityManager.RunningTaskInfo>
            tasks = am.getRunningTasks(1)
            val running = tasks[0]
            if (running.topActivity.packageName != packageName) {
                willLock = true
            }
        }
    }
}
