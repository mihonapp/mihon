package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri

abstract class AbstractBackupRestoreValidator {
    abstract fun validate(context: Context, uri: Uri): Results

    data class Results(val missingSources: List<String>, val missingTrackers: List<String>)
}
