package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService

val Context.connectivityManager: ConnectivityManager
    get() = getSystemService()!!

val Context.wifiManager: WifiManager
    get() = getSystemService()!!

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
