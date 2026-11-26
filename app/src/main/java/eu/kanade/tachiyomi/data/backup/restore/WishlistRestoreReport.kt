package eu.kanade.tachiyomi.data.backup.restore

data class WishlistRestoreReport(
    var restored: Int = 0,
    var skipped: Int = 0,
    var conflicted: Int = 0,
    var categoryWarnings: Int = 0,
    var complete: Boolean = true,
)
