package eu.kanade.tachiyomi.util.system

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.lang.truncateCenter
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies a string to clipboard
 *
 * @param label Label to show to the user describing the content
 * @param content the actual text to copy to the board
 */
fun Context.copyToClipboard(label: String, content: String) {
    if (content.isBlank()) return

    try {
        val clipboard = getSystemService<ClipboardManager>()!!
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))

        // Android 13 and higher shows a visual confirmation of copied contents
        // https://developer.android.com/about/versions/13/features/copy-paste
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            toast(getString(R.string.copied_to_clipboard, content.truncateCenter(50)))
        }
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e)
        toast(R.string.clipboard_copy_error)
    }
}

/**
 * Helper method to create a notification builder.
 *
 * @param id the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
fun Context.notificationBuilder(channelId: String, block: (NotificationCompat.Builder.() -> Unit)? = null): NotificationCompat.Builder {
    val builder = NotificationCompat.Builder(this, channelId)
        .setColor(getColor(R.color.accent_blue))
    if (block != null) {
        builder.block()
    }
    return builder
}

/**
 * Helper method to create a notification.
 *
 * @param id the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
fun Context.notification(channelId: String, block: (NotificationCompat.Builder.() -> Unit)?): Notification {
    val builder = notificationBuilder(channelId, block)
    return builder.build()
}

/**
 * Checks if the give permission is granted.
 *
 * @param permission the permission to check.
 * @return true if it has permissions.
 */
fun Context.hasPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Returns the color for the given attribute.
 *
 * @param resource the attribute.
 * @param alphaFactor the alpha number [0,1].
 */
@ColorInt fun Context.getResourceColor(@AttrRes resource: Int, alphaFactor: Float = 1f): Int {
    val typedArray = obtainStyledAttributes(intArrayOf(resource))
    val color = typedArray.getColor(0, 0)
    typedArray.recycle()

    if (alphaFactor < 1f) {
        val alpha = (color.alpha * alphaFactor).roundToInt()
        return Color.argb(alpha, color.red, color.green, color.blue)
    }

    return color
}

@ColorInt fun Context.getThemeColor(attr: Int): Int {
    val tv = TypedValue()
    return if (this.theme.resolveAttribute(attr, tv, true)) {
        if (tv.resourceId != 0) {
            getColor(tv.resourceId)
        } else {
            tv.data
        }
    } else {
        0
    }
}

val getDisplayMaxHeightInPx: Int
    get() = Resources.getSystem().displayMetrics.let { max(it.heightPixels, it.widthPixels) }

/**
 * Converts to dp.
 */
val Int.pxToDp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

/**
 * Converts to px.
 */
val Int.dpToPx: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

/**
 * Converts to px and takes into account LTR/RTL layout.
 */
val Float.dpToPxEnd: Float
    get() = (
        this * Resources.getSystem().displayMetrics.density *
            if (Resources.getSystem().isLTR) 1 else -1
        )

val Resources.isLTR
    get() = configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR

val Context.notificationManager: NotificationManager
    get() = getSystemService()!!

val Context.connectivityManager: ConnectivityManager
    get() = getSystemService()!!

val Context.wifiManager: WifiManager
    get() = getSystemService()!!

val Context.powerManager: PowerManager
    get() = getSystemService()!!

val Context.keyguardManager: KeyguardManager
    get() = getSystemService()!!

val Context.inputMethodManager: InputMethodManager
    get() = getSystemService()!!

val Context.displayCompat: Display?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        getSystemService<WindowManager>()?.defaultDisplay
    }

/** Gets the duration multiplier for general animations on the device
 * @see Settings.Global.ANIMATOR_DURATION_SCALE
 */
val Context.animatorDurationScale: Float
    get() = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * Convenience method to acquire a partial wake lock.
 */
fun Context.acquireWakeLock(tag: String): PowerManager.WakeLock {
    val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$tag:WakeLock")
    wakeLock.acquire()
    return wakeLock
}

/**
 * Returns true if the given service class is running.
 */
fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val className = serviceClass.name
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { className == it.service.className }
}

fun Context.openInBrowser(url: String, forceDefaultBrowser: Boolean = false) {
    this.openInBrowser(url.toUri(), forceDefaultBrowser)
}

fun Context.openInBrowser(uri: Uri, forceDefaultBrowser: Boolean = false) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // Force default browser so that verified extensions don't re-open Tachiyomi
            if (forceDefaultBrowser) {
                defaultBrowserPackageName()?.let { setPackage(it) }
            }
        }
        startActivity(intent)
    } catch (e: Exception) {
        toast(e.message)
    }
}

fun Context.defaultBrowserPackageName(): String? {
    val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(browserIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolveInfo
        ?.activityInfo?.packageName
        ?.takeUnless { it in DeviceUtil.invalidDefaultBrowsers }
}

fun Context.createFileInCacheDir(name: String): File {
    val file = File(externalCacheDir, name)
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
    return file
}

private const val TABLET_UI_REQUIRED_SCREEN_WIDTH_DP = 720

// some tablets have screen width like 711dp = 1600px / 2.25
private const val TABLET_UI_MIN_SCREEN_WIDTH_PORTRAIT_DP = 700

// make sure icons on the nav rail fit
private const val TABLET_UI_MIN_SCREEN_WIDTH_LANDSCAPE_DP = 600

fun Context.isTabletUi(): Boolean {
    return resources.configuration.isTabletUi()
}

fun Configuration.isTabletUi(): Boolean {
    return smallestScreenWidthDp >= TABLET_UI_REQUIRED_SCREEN_WIDTH_DP
}

fun Configuration.isAutoTabletUiAvailable(): Boolean {
    return smallestScreenWidthDp >= TABLET_UI_MIN_SCREEN_WIDTH_LANDSCAPE_DP
}

// TODO: move the logic to `isTabletUi()` when main activity is rewritten in Compose
fun Context.prepareTabletUiContext(): Context {
    val configuration = resources.configuration
    val expected = when (Injekt.get<UiPreferences>().tabletUiMode().get()) {
        TabletUiMode.AUTOMATIC ->
            configuration.smallestScreenWidthDp >= when (configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> TABLET_UI_MIN_SCREEN_WIDTH_PORTRAIT_DP
                else -> TABLET_UI_MIN_SCREEN_WIDTH_LANDSCAPE_DP
            }
        TabletUiMode.ALWAYS -> true
        TabletUiMode.LANDSCAPE -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        TabletUiMode.NEVER -> false
    }
    if (configuration.isTabletUi() != expected) {
        val overrideConf = Configuration()
        overrideConf.setTo(configuration)
        overrideConf.smallestScreenWidthDp = if (expected) {
            overrideConf.smallestScreenWidthDp.coerceAtLeast(TABLET_UI_REQUIRED_SCREEN_WIDTH_DP)
        } else {
            overrideConf.smallestScreenWidthDp.coerceAtMost(TABLET_UI_REQUIRED_SCREEN_WIDTH_DP - 1)
        }
        return createConfigurationContext(overrideConf)
    }
    return this
}

/**
 * Returns true if current context is in night mode
 */
fun Context.isNightMode(): Boolean {
    return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

/**
 * Creates night mode Context depending on reader theme/background
 *
 * Context wrapping method obtained from AppCompatDelegateImpl
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:appcompat/appcompat/src/main/java/androidx/appcompat/app/AppCompatDelegateImpl.java;l=348;drc=e28752c96fc3fb4d3354781469a1af3dbded4898
 */
fun Context.createReaderThemeContext(): Context {
    val preferences = Injekt.get<UiPreferences>()
    val readerPreferences = Injekt.get<ReaderPreferences>()
    val isDarkBackground = when (readerPreferences.readerTheme().get()) {
        1, 2 -> true // Black, Gray
        3 -> applicationContext.isNightMode() // Automatic bg uses activity background by default
        else -> false // White
    }
    val expected = if (isDarkBackground) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != expected) {
        val overrideConf = Configuration()
        overrideConf.setTo(resources.configuration)
        overrideConf.uiMode = (overrideConf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or expected

        val wrappedContext = ContextThemeWrapper(this, R.style.Theme_Tachiyomi)
        wrappedContext.applyOverrideConfiguration(overrideConf)
        ThemingDelegate.getThemeResIds(preferences.appTheme().get(), preferences.themeDarkAmoled().get())
            .forEach { wrappedContext.theme.applyStyle(it, true) }
        return wrappedContext
    }
    return this
}

fun Context.isOnline(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    val maxTransport = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> NetworkCapabilities.TRANSPORT_LOWPAN
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> NetworkCapabilities.TRANSPORT_WIFI_AWARE
        else -> NetworkCapabilities.TRANSPORT_VPN
    }
    return (NetworkCapabilities.TRANSPORT_CELLULAR..maxTransport).any(networkCapabilities::hasTransport)
}

/**
 * Returns true if device is connected to Wifi.
 */
fun Context.isConnectedToWifi(): Boolean {
    if (!wifiManager.isWifiEnabled) return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo.bssid != null
    }
}

/**
 * Gets document size of provided [Uri]
 *
 * @return document size of [uri] or null if size can't be obtained
 */
fun Context.getUriSize(uri: Uri): Long? {
    return UniFile.fromUri(this, uri).length().takeIf { it >= 0 }
}

/**
 * Returns true if [packageName] is installed.
 */
fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.isInstalledFromFDroid(): Boolean {
    val installerPackageName = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    } catch (e: Exception) {
        null
    }

    return installerPackageName == "org.fdroid.fdroid" ||
        // F-Droid builds typically disable the updater
        (!BuildConfig.INCLUDE_UPDATER && !isDevFlavor)
}

fun Context.getApplicationIcon(pkgName: String): Drawable? {
    return try {
        packageManager.getApplicationIcon(pkgName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
