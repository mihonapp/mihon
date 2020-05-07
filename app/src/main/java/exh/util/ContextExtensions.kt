package exh.util

import android.app.job.JobScheduler
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager

/**
 * Property to get the wifi manager from the context.
 */
val Context.wifiManager: WifiManager
    get() = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

val Context.clipboardManager: ClipboardManager
    get() = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

val Context.jobScheduler: JobScheduler
    get() = applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
