package eu.kanade.tachiyomi.data.gorse

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Service for interacting with Gorse recommendation system
 */
class GorseService(
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val preferences: GorsePreferences = Injekt.get(),
) {
    private val api = GorseApi(networkHelper.client, preferences)
    
    private val _events = MutableSharedFlow<GorseEvent>()
    val events: SharedFlow<GorseEvent> = _events.asSharedFlow()

    companion object {
        private const val USER_ID = "mihon"
    }

    /**
     * Mark manga as read in Gorse
     */
    suspend fun markMangaRead(manga: Manga): Result<Unit> {
        return try {
            val itemId = manga.title  // 使用漫画名称作为itemId
            logcat(LogPriority.DEBUG) { "Marking manga '${manga.title}' as read in Gorse (itemId: $itemId)" }
            val result = api.markItemRead(USER_ID, itemId)
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully marked manga '${manga.title}' as read in Gorse" }
                _events.emit(GorseEvent.ItemMarkedRead(manga.title, "已读标记成功发送"))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to mark manga '${manga.title}' as read in Gorse" }
                _events.emit(GorseEvent.ItemMarkedReadFailed(manga.title, e.message ?: "未知错误"))
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markMangaRead for manga '${manga.title}'" }
            _events.emit(GorseEvent.ItemMarkedReadFailed(manga.title, e.message ?: "异常: ${e.javaClass.simpleName}"))
            Result.failure(e)
        }
    }

    /**
     * Mark manga as liked in Gorse
     */
    suspend fun markMangaLiked(manga: Manga): Result<Unit> {
        return try {
            val itemId = manga.title  // 使用漫画名称作为itemId
            logcat(LogPriority.DEBUG) { "Marking manga '${manga.title}' as liked in Gorse (itemId: $itemId)" }
            val result = api.markItemLiked(USER_ID, itemId)
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully marked manga '${manga.title}' as liked in Gorse" }
                _events.emit(GorseEvent.ItemLiked(manga.title, "喜欢标记成功发送"))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to mark manga '${manga.title}' as liked in Gorse" }
                _events.emit(GorseEvent.ItemLikedFailed(manga.title, e.message ?: "未知错误"))
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markMangaLiked for manga '${manga.title}'" }
            _events.emit(GorseEvent.ItemLikedFailed(manga.title, e.message ?: "异常: ${e.javaClass.simpleName}"))
            Result.failure(e)
        }
    }

    /**
     * Remove like from manga in Gorse (unlike)
     */
    suspend fun removeMangaLike(manga: Manga): Result<Unit> {
        return try {
            val itemId = manga.title  // 使用漫画名称作为itemId
            logcat(LogPriority.DEBUG) { "Removing like from manga '${manga.title}' in Gorse (itemId: $itemId)" }
            val result = api.removeItemLike(USER_ID, itemId)
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully removed like from manga '${manga.title}' in Gorse" }
                _events.emit(GorseEvent.ItemUnliked(manga.title))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to remove like from manga '${manga.title}' in Gorse" }
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in removeMangaLike for manga '${manga.title}'" }
            Result.failure(e)
        }
    }

    /**
     * Mark a recommendation item as hidden (not interested)
     */
    suspend fun markItemAsHidden(itemId: String): Result<Unit> {
        return try {
            logcat(LogPriority.DEBUG) { "Marking item '$itemId' as hidden in Gorse" }
            val result = api.markItemAsHidden(itemId)
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully marked item '$itemId' as hidden in Gorse" }
                _events.emit(GorseEvent.ItemHidden(itemId))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to mark item '$itemId' as hidden in Gorse" }
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markItemAsHidden for item '$itemId'" }
            Result.failure(e)
        }
    }

    /**
     * Ensure user exists in Gorse
     */
    private suspend fun ensureUserExists(): Result<Unit> {
        return try {
            api.insertUser(USER_ID)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to ensure user exists" }
            Result.failure(e)
        }
    }

    /**
     * Ensure manga metadata exists in Gorse
     */
    private suspend fun ensureMangaExists(manga: Manga): Result<Unit> {
        return try {
            val categories = if (!manga.genre.isNullOrEmpty()) {
                manga.genre!!
            } else {
                emptyList()
            }
            // 使用漫画标题作为itemId
            api.insertMangaMetadata(manga.title, manga.title, categories)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to ensure manga exists" }
            Result.failure(e)
        }
    }

    /**
     * Get similar manga recommendations
     * Returns list of GorseRecommendationItem sorted by score (highest first)
     */
    suspend fun getSimilarManga(manga: Manga, count: Int = 10): List<GorseRecommendationItem> {
        return try {
            val itemId = manga.title  // 使用漫画名称作为itemId
            logcat(LogPriority.DEBUG) { "Getting similar manga for '${manga.title}' (itemId: $itemId)" }
            val result = api.getSimilarItems(itemId, count)
            if (result.isSuccess) {
                val neighbors = result.getOrElse { emptyList() }
                // Convert GorseNeighborItem to GorseRecommendationItem
                neighbors.map { neighbor ->
                    GorseRecommendationItem(
                        itemId = neighbor.id,
                        score = neighbor.score,
                        categories = emptyList(),
                        labels = emptyList(),
                        timestamp = "",
                        comment = "",
                        isHidden = false
                    )
                }.also {
                    logcat(LogPriority.INFO) { "Got ${it.size} similar manga for '${manga.title}'" }
                }
            } else {
                logcat(LogPriority.ERROR) { "Failed to get similar manga from Gorse: ${result.exceptionOrNull()?.message}" }
                emptyList()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error getting similar manga from Gorse" }
            emptyList()
        }
    }

    /**
     * Get personalized recommendations for the user
     */
    suspend fun getRecommendations(count: Int = 20): List<GorseRecommendationItem> {
        return try {
            val result = api.getRecommendations(USER_ID, count)
            if (result.isSuccess) {
                val itemIds = result.getOrElse { emptyList() }
                // 将字符串列表转换为GorseRecommendationItem对象，分数设为默认值
                itemIds.mapIndexed { index, itemId ->
                    GorseRecommendationItem(
                        itemId = itemId,
                        score = 1.0 - (index * 0.05), // 递减分数，第一个1.0，第二个0.95，以此类推
                        categories = emptyList(),
                        labels = emptyList(),
                        timestamp = "",
                        comment = "",
                        isHidden = false
                    )
                }
            } else {
                logcat(LogPriority.ERROR) { "Failed to get recommendations from Gorse: ${result.exceptionOrNull()?.message}" }
                emptyList()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error getting recommendations from Gorse" }
            emptyList()
        }
    }
}

sealed class GorseEvent {
    data class ItemMarkedRead(val title: String, val message: String = "") : GorseEvent()
    data class ItemMarkedReadFailed(val title: String, val error: String) : GorseEvent()
    data class ItemLiked(val title: String, val message: String = "") : GorseEvent()
    data class ItemLikedFailed(val title: String, val error: String) : GorseEvent()
    data class ItemUnliked(val title: String) : GorseEvent()
    data class ItemHidden(val itemId: String) : GorseEvent()
    data class Error(val message: String) : GorseEvent()
}
