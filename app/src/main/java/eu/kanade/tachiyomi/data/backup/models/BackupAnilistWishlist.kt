package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupAnilistWishlist(
    @ProtoNumber(1) val mappings: List<BackupAnilistWishlistMapping> = emptyList(),
    @ProtoNumber(2) val category: BackupWishlistCategory? = null,
)

@Serializable
data class BackupAnilistWishlistMapping(
    @ProtoNumber(1) val anilistMediaId: Long,
    @ProtoNumber(2) val sourceId: Long,
    @ProtoNumber(3) val url: String,
)

@Serializable
data class BackupWishlistCategory(
    @ProtoNumber(1) val backupCategoryId: Long? = null,
    @ProtoNumber(2) val name: String,
)
