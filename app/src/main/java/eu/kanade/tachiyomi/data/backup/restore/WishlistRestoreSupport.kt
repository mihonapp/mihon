package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlist
import eu.kanade.tachiyomi.data.backup.models.BackupAnilistWishlistMapping
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupWishlistCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tachiyomi.domain.anilist.model.AnilistWishlistMapping
import tachiyomi.domain.anilist.repository.AnilistWishlistRepository.InsertResult
import tachiyomi.domain.category.model.Category

internal data class WishlistMappingRestorePlan(
    val mappings: List<BackupAnilistWishlistMapping>,
    val skipped: Int,
)

internal fun shouldRestoreWishlistMappings(libraryEntriesEnabled: Boolean, wishlist: BackupAnilistWishlist?) =
    libraryEntriesEnabled && wishlist != null

internal fun planWishlistMappingRestore(wishlist: BackupAnilistWishlist): WishlistMappingRestorePlan {
    val records = wishlist.mappings.distinct()
    val ambiguous = records.filter { mapping ->
        records.any { other ->
            other != mapping && (
                (other.anilistMediaId == mapping.anilistMediaId && other.identity() != mapping.identity()) ||
                    (other.identity() == mapping.identity() && other.anilistMediaId != mapping.anilistMediaId)
                )
        }
    }.toSet()
    val invalid = records.filter { it.anilistMediaId <= 0 || it.url.isBlank() }.toSet()
    val skipped = ambiguous + invalid
    return WishlistMappingRestorePlan(records.filterNot { it in skipped }, skipped.size)
}

internal data class WishlistCategoryResolution(
    val destinationId: Long?,
    val warning: Boolean,
)

internal fun resolveWishlistCategory(
    reference: BackupWishlistCategory?,
    appSettingsEnabled: Boolean,
    categoriesEnabled: Boolean,
    backupCategories: List<BackupCategory>,
    restoredCategoryIds: Map<Long, Long>,
    destinationCategories: List<Category>,
): WishlistCategoryResolution {
    if (!appSettingsEnabled) return WishlistCategoryResolution(null, false)
    if (reference == null) return WishlistCategoryResolution(null, true)

    val destinationId = if (categoriesEnabled) {
        backupCategories.singleOrNull {
            it.id == reference.backupCategoryId && it.name == reference.name
        }?.let { restoredCategoryIds[it.id] }
    } else {
        destinationCategories.singleOrNull { it.name == reference.name }?.id
    }
    return WishlistCategoryResolution(destinationId, destinationId == null)
}

internal suspend fun processWishlistMappings(
    wishlist: BackupAnilistWishlist,
    report: WishlistRestoreReport,
    resolveMangaId: suspend (sourceId: Long, url: String) -> Long?,
    insertIfAbsent: suspend (AnilistWishlistMapping) -> InsertResult,
) {
    try {
        currentCoroutineContext().ensureActive()
        val plan = planWishlistMappingRestore(wishlist)
        report.skipped += plan.skipped
        plan.mappings.forEach { mapping ->
            currentCoroutineContext().ensureActive()
            val mangaId = resolveMangaId(mapping.sourceId, mapping.url)
            if (mangaId == null) {
                report.skipped++
                return@forEach
            }
            when (insertIfAbsent(AnilistWishlistMapping(mapping.anilistMediaId, mangaId))) {
                InsertResult.Inserted,
                InsertResult.AlreadyMapped,
                -> report.restored++
                InsertResult.Conflict -> report.conflicted++
                InsertResult.Invalid -> report.skipped++
            }
        }
    } catch (e: CancellationException) {
        report.complete = false
        throw e
    } catch (e: Throwable) {
        report.complete = false
        throw e
    }
}

private fun BackupAnilistWishlistMapping.identity() = sourceId to url
