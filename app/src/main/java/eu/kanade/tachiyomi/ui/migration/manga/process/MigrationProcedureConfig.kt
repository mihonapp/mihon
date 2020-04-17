package eu.kanade.tachiyomi.ui.migration.manga.process

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MigrationProcedureConfig(
    var mangaIds: List<Long>,
    val extraSearchParams: String?
) : Parcelable
