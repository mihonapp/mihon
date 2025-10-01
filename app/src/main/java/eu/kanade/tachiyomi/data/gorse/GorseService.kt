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
            // 首先确保用户存在
            ensureUserExists().onFailure { 
                logcat(LogPriority.WARN) { "Failed to ensure user exists for read action" }
                // 继续执行，即使用户创建失败
            }
            
            // 确保漫画元数据存在
            ensureMangaExists(manga).onFailure {
                logcat(LogPriority.WARN) { "Failed to ensure manga exists for read action" }
                // 继续执行，即使漫画创建失败
            }
            
            logcat(LogPriority.DEBUG) { "Marking manga '${manga.title}' as read in Gorse" }
            val result = api.markItemRead(USER_ID, manga.id.toString())
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully marked manga '${manga.title}' as read in Gorse" }
                _events.emit(GorseEvent.ItemMarkedRead(manga.title))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to mark manga '${manga.title}' as read in Gorse" }
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markMangaRead for manga '${manga.title}'" }
            Result.failure(e)
        }
    }

    /**
     * Mark manga as liked in Gorse
     */
    suspend fun markMangaLiked(manga: Manga): Result<Unit> {
        return try {
            // 首先确保用户存在
            ensureUserExists().onFailure { 
                logcat(LogPriority.WARN) { "Failed to ensure user exists for like action" }
                // 继续执行，即使用户创建失败
            }
            
            // 确保漫画元数据存在
            ensureMangaExists(manga).onFailure {
                logcat(LogPriority.WARN) { "Failed to ensure manga exists for like action" }
                // 继续执行，即使漫画创建失败
            }
            
            logcat(LogPriority.DEBUG) { "Marking manga '${manga.title}' as liked in Gorse" }
            val result = api.markItemLiked(USER_ID, manga.id.toString())
            result.onSuccess {
                logcat(LogPriority.INFO) { "Successfully marked manga '${manga.title}' as liked in Gorse" }
                _events.emit(GorseEvent.ItemLiked(manga.title))
            }.onFailure { e ->
                logcat(LogPriority.ERROR, e) { "Failed to mark manga '${manga.title}' as liked in Gorse" }
            }
            result
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markMangaLiked for manga '${manga.title}'" }
            Result.failure(e)
        }
    }

    /**
     * Remove like from manga in Gorse (unlike)
     */
    suspend fun removeMangaLike(manga: Manga): Result<Unit> {
        return try {
            logcat(LogPriority.DEBUG) { "Removing like from manga '${manga.title}' in Gorse" }
            val result = api.removeItemLike(USER_ID, manga.id.toString())
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
            api.insertMangaMetadata(manga.id.toString(), manga.title, categories)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to ensure manga exists" }
            Result.failure(e)
        }
    }

    /**
     * Get similar manga recommendations
     */
    suspend fun getSimilarManga(manga: Manga, count: Int = 3): List<String> {
        return try {
            val itemId = manga.id.toString()
            val result = api.getSimilarItems(itemId, count)
            if (result.isSuccess) {
                result.getOrElse { emptyList() }
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
    data class ItemMarkedRead(val title: String) : GorseEvent()
    data class ItemLiked(val title: String) : GorseEvent()
    data class ItemUnliked(val title: String) : GorseEvent()
    data class Error(val message: String) : GorseEvent()
}
