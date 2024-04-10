package eu.kanade.core.util

import android.os.Build

// A pair of device and model names that uniquely identify an InkBook E-Ink device.
private val lookupTable = listOf(
    // https://web.archive.org/web/20240410001703/https://inkbook.eu/products/inkbook-focus
    "px30_eink" to "Focus"
)

/**
 * Returns true if the device is an InkBook E-Ink device.
 */
internal fun isInkBookEInkDevice(): Boolean =
    (Build.DEVICE to Build.MODEL) in lookupTable
