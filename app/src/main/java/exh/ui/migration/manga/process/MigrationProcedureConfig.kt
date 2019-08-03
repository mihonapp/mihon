package exh.ui.migration.manga.process

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MigrationProcedureConfig(
        val mangaIds: List<Long>,
        val targetSourceIds: List<Long>,
        val useSourceWithMostChapters: Boolean,
        val enableLenientSearch: Boolean,
        val migrationFlags: Int,
        val copy: Boolean,
        val extraSearchParams: String?
): Parcelable