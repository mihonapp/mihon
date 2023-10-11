package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(100) var backupBrokenSources: List<BrokenBackupSource> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
) {

    companion object {
        val filenameRegex = """${BuildConfig.APPLICATION_ID}_\d+-\d+-\d+_\d+-\d+.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
