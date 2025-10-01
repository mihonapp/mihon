package eu.kanade.tachiyomi.data.gorse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API client for Gorse recommendation engine
 */
class GorseApi(
    private val client: OkHttpClient,
    private val preferences: GorsePreferences,
) {

    private val baseUrl: String
        get() = preferences.gorseServerUrl().get()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Get current timestamp in ISO 8601 format for Gorse API
     */
    private fun getCurrentISOTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    /**
     * Mark an item as read by a user
     */
    suspend fun markItemRead(userId: String, itemId: String): Result<Unit> = withIOContext {
        try {
            val feedback = listOf(GorseFeedback(
                feedbackType = "read",
                userId = userId,
                itemId = itemId,
                timestamp = getCurrentISOTimestamp()
            ))

            val requestBody = json.encodeToString(feedback)
                .toRequestBody("application/json".toMediaType())

            logcat(LogPriority.DEBUG) { "Gorse markItemRead request: ${json.encodeToString(feedback)}" }
            logcat(LogPriority.DEBUG) { "Gorse markItemRead URL: $baseUrl/api/feedback" }

            val request = Request.Builder()
                .url("$baseUrl/api/feedback")
                .post(requestBody)  // 根据API文档，已读使用POST方法
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse markItemRead response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse markItemRead response body: $responseBody" }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark item as read: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markItemRead" }
            Result.failure(e)
        }
    }

    /**
     * Mark an item as liked by a user
     */
    suspend fun markItemLiked(userId: String, itemId: String): Result<Unit> = withIOContext {
        try {
            val feedback = listOf(GorseFeedback(
                feedbackType = "like",
                userId = userId,
                itemId = itemId,
                timestamp = getCurrentISOTimestamp()
            ))

            val requestBody = json.encodeToString(feedback)
                .toRequestBody("application/json".toMediaType())

            logcat(LogPriority.DEBUG) { "Gorse markItemLiked request: ${json.encodeToString(feedback)}" }
            logcat(LogPriority.DEBUG) { "Gorse markItemLiked URL: $baseUrl/api/feedback" }

            val request = Request.Builder()
                .url("$baseUrl/api/feedback")
                .post(requestBody)  // 根据API文档，喜欢使用POST方法
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse markItemLiked response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse markItemLiked response body: $responseBody" }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark item as liked: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in markItemLiked" }
            Result.failure(e)
        }
    }

    /**
     * Remove like from an item (unlike)
     */
    suspend fun removeItemLike(userId: String, itemId: String): Result<Unit> = withIOContext {
        try {
            logcat(LogPriority.DEBUG) { "Gorse removeItemLike URL: $baseUrl/api/feedback/like/$userId/$itemId" }

            val request = Request.Builder()
                .url("$baseUrl/api/feedback/like/$userId/$itemId")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse removeItemLike response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse removeItemLike response body: $responseBody" }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove item like: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in removeItemLike" }
            Result.failure(e)
        }
    }

    /**
     * Get recommendations for a user
     * Returns a simple list of item IDs (strings)
     */
    suspend fun getRecommendations(userId: String, n: Int = 20): Result<List<String>> = withIOContext {
        var responseBody: String? = null
        try {
            logcat(LogPriority.DEBUG) { "Gorse getRecommendations URL: $baseUrl/api/recommend/$userId?n=$n" }

            val request = Request.Builder()
                .url("$baseUrl/api/recommend/$userId?n=$n")
                .get()
                .build()

            val response = client.newCall(request).execute()
            responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse getRecommendations response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse getRecommendations response body: $responseBody" }

            if (response.isSuccessful && responseBody != null) {
                val recommendations = json.decodeFromString<List<String>>(responseBody)
                logcat(LogPriority.DEBUG) { "Gorse getRecommendations parsed: ${recommendations.size} items" }
                Result.success(recommendations)
            } else {
                Result.failure(Exception("Failed to get recommendations: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in getRecommendations" }
            logcat(LogPriority.ERROR) { "JSON input: $responseBody" }
            Result.failure(e)
        }
    }

    /**
     * Get items similar to a given item
     */
    suspend fun getSimilarItems(itemId: String, n: Int = 3): Result<List<String>> = withIOContext {
        try {
            logcat(LogPriority.DEBUG) { "Gorse getSimilarItems URL: $baseUrl/api/item/$itemId/neighbors?n=$n" }

            val request = Request.Builder()
                .url("$baseUrl/api/item/$itemId/neighbors?n=$n")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse getSimilarItems response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse getSimilarItems response body: $responseBody" }

            if (response.isSuccessful && responseBody != null) {
                val similarItems = json.decodeFromString<List<String>>(responseBody)
                Result.success(similarItems)
            } else {
                Result.failure(Exception("Failed to get similar items: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in getSimilarItems" }
            Result.failure(e)
        }
    }

    /**
     * Insert or update a user in Gorse
     */
    suspend fun insertUser(userId: String): Result<Unit> = withIOContext {
        try {
            val userData = GorseUser(
                userId = userId,
                labels = emptyList(),
                subscribe = emptyList(),
                comment = "Mihon user"
            )

            val requestBody = json.encodeToString(GorseUser.serializer(), userData)
                .toRequestBody("application/json".toMediaType())

            logcat(LogPriority.DEBUG) { "Gorse insertUser request: ${json.encodeToString(GorseUser.serializer(), userData)}" }
            logcat(LogPriority.DEBUG) { "Gorse insertUser URL: $baseUrl/api/user" }

            val request = Request.Builder()
                .url("$baseUrl/api/user")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse insertUser response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse insertUser response body: $responseBody" }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to insert user: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in insertUser" }
            Result.failure(e)
        }
    }

    /**
     * Insert or update manga metadata in Gorse
     */
    suspend fun insertMangaMetadata(mangaId: String, title: String, categories: List<String> = emptyList()): Result<Unit> = withIOContext {
        try {
            val item = GorseItem(
                itemId = mangaId,
                isHidden = false,
                categories = categories,
                timestamp = getCurrentISOTimestamp(),  // 使用 ISO 格式
                labels = listOf(title),
                comment = title
            )

            val requestBody = json.encodeToString(GorseItem.serializer(), item)
                .toRequestBody("application/json".toMediaType())

            logcat(LogPriority.DEBUG) { "Gorse insertMangaMetadata request: ${json.encodeToString(GorseItem.serializer(), item)}" }
            logcat(LogPriority.DEBUG) { "Gorse insertMangaMetadata URL: $baseUrl/api/item" }

            val request = Request.Builder()
                .url("$baseUrl/api/item")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            logcat(LogPriority.DEBUG) { "Gorse insertMangaMetadata response code: ${response.code}" }
            logcat(LogPriority.DEBUG) { "Gorse insertMangaMetadata response body: $responseBody" }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to insert manga metadata: ${response.code}, body: $responseBody"))
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Exception in insertMangaMetadata" }
            Result.failure(e)
        }
    }
}

@Serializable
data class GorseFeedback(
    @SerialName("FeedbackType") val feedbackType: String,
    @SerialName("UserId") val userId: String,
    @SerialName("ItemId") val itemId: String,
    @SerialName("Timestamp") val timestamp: String,
)

@Serializable
data class GorseUser(
    @SerialName("UserId") val userId: String,
    @SerialName("Labels") val labels: List<String> = emptyList(),
    @SerialName("Subscribe") val subscribe: List<String> = emptyList(),
    @SerialName("Comment") val comment: String = "",
)

@Serializable
data class GorseItem(
    @SerialName("ItemId") val itemId: String,
    @SerialName("IsHidden") val isHidden: Boolean = false,
    @SerialName("Categories") val categories: List<String> = emptyList(),
    @SerialName("Timestamp") val timestamp: String,
    @SerialName("Labels") val labels: List<String> = emptyList(),
    @SerialName("Comment") val comment: String = "",
)

@Serializable
data class GorseRecommendationItem(
    @SerialName("ItemId") val itemId: String,
    @SerialName("Categories") val categories: List<String> = emptyList(),
    @SerialName("Comment") val comment: String = "",
    @SerialName("IsHidden") val isHidden: Boolean = false,
    @SerialName("Labels") val labels: List<String> = emptyList(),
    @SerialName("Score") val score: Double = 0.0,
    @SerialName("Timestamp") val timestamp: String = "",
)
