package mihon.desktop.track

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Token entry uses an existing authorized token; no Android OAuth client is impersonated. */
abstract class JsonTokenTracker(
    id: Long,
    name: String,
    baseUrl: String,
    client: OkHttpClient,
    private val authHeader: String = "Authorization",
    private val authPrefix: String = "Bearer ",
) : BaseDesktopTracker(id, name, TrackerAuthType.TOKEN) {
    private val http = TrackerHttpClient(baseUrl, client)
    protected abstract val profilePath: String
    protected abstract fun account(profile: JsonObject): String?
    override suspend fun login(credentials: Map<String, String>): Boolean {
        val candidate = credentials["token"]?.trim().orEmpty()
        if (candidate.isBlank()) return false
        return try {
            val profile = request(profilePath, credential = candidate)
            val user = account(profile) ?: throw TrackerApiException("$name did not return an account")
            setLoggedIn(true, user, candidate)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }
    override fun restoreLogin(info: TrackerLoginInfo) {
        if (info.token.isNotBlank()) setLoggedIn(true, info.username, info.token)
    }
    protected suspend fun request(
        path: String,
        method: String = "GET",
        payload: JsonObject? = null,
        credential: String? = token,
    ): JsonObject {
        val builder = Request.Builder().url(http.baseUrl + path)
            .header("User-Agent", "MihonW/0.1 (Windows)")
        credential?.let { builder.header(authHeader, authPrefix + it) }
        if (method != "GET") builder.method(method, payload?.toString()?.toRequestBody(TRACKER_JSON_MEDIA_TYPE))
        val body = http.execute(builder.build())
        return if (body.isBlank()) buildJsonObject { } else defaultTrackerJson.parseToJsonElement(body).jsonObject
    }
    protected suspend fun optional(
        path: String,
    ): JsonObject? = try {
        request(path)
    } catch (
        error: TrackerHttpException,
    ) {
        if (error.code == 404) null else throw error
    }
}

internal fun JsonObject.tokenText(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.tokenNumber(key: String): Double = tokenText(key)?.toDoubleOrNull() ?: 0.0
internal fun JsonObject.tokenObject(key: String): JsonObject = get(key) as? JsonObject ?: buildJsonObject { }
internal fun JsonObject.tokenArray(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
