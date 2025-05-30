package eu.kanade.tachiyomi.data.track

import java.io.IOException

/**
 * Thrown when the OAuth for a tracker has expired and renewing is not possible either due to expired refresh
 * token or not being provided a way to do so by service.
 */
class TrackerOAuthRefreshNotPossibleException(
    val tracker: Tracker,
) : IOException("${tracker.name}: OAuth refresh not possible.")
