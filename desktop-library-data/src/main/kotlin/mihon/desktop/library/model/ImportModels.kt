package mihon.desktop.library.model

enum class ImportType {
    ANDROID_BACKUP,
    LOCAL_DIRECTORY,
}

enum class ImportStatus {
    SUCCEEDED,
    REJECTED,
    FAILED,
}

enum class PreferenceSkipReason {
    PRIVATE,
    APP_STATE,
    UNKNOWN,
    UNSUPPORTED_TYPE,
}

data class ImportCounts(
    val mangaInserted: Long = 0,
    val mangaMerged: Long = 0,
    val chaptersInserted: Long = 0,
    val chaptersMerged: Long = 0,
    val categoriesLinked: Long = 0,
    val preferencesImported: Long = 0,
    val preferencesSkipped: Long = 0,
)

data class ImportReportItem(
    val itemType: String,
    val itemKey: String,
    val outcome: String,
    val reason: String? = null,
    val message: String,
    val id: Long = 0,
)

data class ImportReport(
    val id: Long = 0,
    val importType: ImportType,
    val sourcePath: String,
    val status: ImportStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val counts: ImportCounts = ImportCounts(),
    val items: List<ImportReportItem> = emptyList(),
)

data class ImportReportRecord(
    val importType: ImportType,
    val sourcePath: String,
    val status: ImportStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val counts: ImportCounts = ImportCounts(),
)

data class ImportReportItemRecord(
    val itemType: String,
    val itemKey: String,
    val outcome: String,
    val reason: String? = null,
    val message: String,
)
