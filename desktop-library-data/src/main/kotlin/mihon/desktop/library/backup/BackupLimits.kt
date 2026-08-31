package mihon.desktop.library.backup

data class BackupLimits(
    val maxCompressedBytes: Long,
    val maxExpandedBytes: Long,
    val maxManga: Int,
    val maxChapters: Int,
    val maxCategories: Int,
    val maxTracks: Int,
    val maxPreferences: Int,
    val maxStringChars: Int,
    val maxNestingDepth: Int,
) {
    companion object {
        val DEFAULT = BackupLimits(
            maxCompressedBytes = 268_435_456,
            maxExpandedBytes = 1_073_741_824,
            maxManga = 100_000,
            maxChapters = 2_000_000,
            maxCategories = 10_000,
            maxTracks = 1_000_000,
            maxPreferences = 100_000,
            maxStringChars = 1_048_576,
            maxNestingDepth = 64,
        )
    }
}
