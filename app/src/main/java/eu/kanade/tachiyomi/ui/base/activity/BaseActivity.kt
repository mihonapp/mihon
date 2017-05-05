package eu.kanade.tachiyomi.ui.base.activity

import android.support.v7.app.AppCompatActivity
import eu.kanade.tachiyomi.util.LocaleHelper
import exh.ui.lock.lockEnabled
import exh.ui.lock.showLockActivity
import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.os.Build
import java.util.*


abstract class BaseActivity : AppCompatActivity(), ActivityMixin {

    override var resumed = false

    init {
        LocaleHelper.updateConfiguration(this)
    }

    override fun getActivity() = this

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
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
