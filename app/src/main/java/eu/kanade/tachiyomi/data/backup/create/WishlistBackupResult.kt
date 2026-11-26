package eu.kanade.tachiyomi.data.backup.create

import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlist
import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlistMapping
import eu.kanade.tachiyomi.data.backup.models.BackupWishlistCategory
import tachiyomi.domain.anilist.model.AnilistWishlistMappingWithMangaIdentity

internal data class WishlistBackupResult(
    val metadata: BackupAnilistWishlist?,
    val skippedMappings: Int,
)

internal fun createWishlistBackup(
    snapshot: List<AnilistWishlistMappingWithMangaIdentity>,
    emittedIdentities: Set<Pair<Long, String>>,
    category: BackupWishlistCategory?,
): WishlistBackupResult {
    val included = snapshot.mapNotNull { mapping ->
        val sourceId = mapping.sourceId ?: return@mapNotNull null
        val url = mapping.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if ((sourceId to url) !in emittedIdentities) return@mapNotNull null
        BackupAnilistWishlistMapping(mapping.anilistMediaId, sourceId, url)
    }
    val metadata = if (included.isNotEmpty() || category != null) {
        BackupAnilistWishlist(included, category)
    } else {
        null
    }
    return WishlistBackupResult(metadata, snapshot.size - included.size)
}
