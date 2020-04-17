package eu.kanade.tachiyomi.ui.migration.manga.process

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MigrationProcedureConfig(
    var mangaIds: List<Long>,
    val targetSourceIds: List<Long>,
    val useSourceWithMostChapters: Boolean,
    val enableLenientSearch: Boolean,
    val migrationFlags: Int,
    val extraSearchParams: String?
) : Parcelable
