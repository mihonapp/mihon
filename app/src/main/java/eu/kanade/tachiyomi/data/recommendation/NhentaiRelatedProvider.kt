package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Optional source-specific candidate generator for nHentai.
 *
 * The endpoint is not part of Mihon's public source API, so every failure is returned as data and
 * callers can silently fall back to the regular source recommendation routes.
 */
internal interface NhentaiRelatedProvider {
    fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget?

    suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome
}

/** Stable coordination identity; transport details remain private to each provider implementation. */
internal interface NhentaiRelatedTarget {
    val sourceId: Long
    val hostKey: String
    val cacheKey: String
}

internal class DefaultNhentaiRelatedProvider(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : NhentaiRelatedProvider {

    override fun resolve(source: Source, manga: SManga): NhentaiRelatedTarget? =
        supportedTarget(source, manga.url)

    override suspend fun load(target: NhentaiRelatedTarget): NhentaiRelatedOutcome {
        val requestTarget = target as? RequestTarget ?: return NhentaiRelatedOutcome.Unsupported
        val requestHeaders = requestTarget.source.headers.newBuilder().apply {
            if (requestTarget.source.headers["Accept"] == null) {
                add("Accept", "application/json")
            }
        }.build()
        val request = GET(
            url = requestTarget.endpoint,
            headers = requestHeaders,
            cache = CacheControl.Builder().noCache().build(),
        )

        return try {
            requestTarget.source.client.newCall(request).await().use { response ->
                when {
                    response.code == HTTP_TOO_MANY_REQUESTS -> {
                        NhentaiRelatedOutcome.RateLimited(parseRetryAfter(response.header("Retry-After")))
                    }
                    !response.isSuccessful -> {
                        NhentaiRelatedOutcome.HttpFailure(
                            statusCode = response.code,
                            classification = classifyHttpFailure(response.code),
                        )
                    }
                    response.header("Content-Type")?.contains("text/html", ignoreCase = true) == true -> {
                        NhentaiRelatedOutcome.InvalidResponse(NhentaiInvalidResponseReason.NON_JSON)
                    }
                    else -> parseSuccess(response.body.string(), requestTarget.galleryId)
                }
            }
        } catch (error: IOException) {
            NhentaiRelatedOutcome.NetworkFailure(error)
        }
    }

    private fun supportedTarget(source: Source, mangaUrl: String): RequestTarget? {
        val httpSource = source as? HttpSource ?: return null
        val baseUrl = httpSource.baseUrl.toHttpUrlOrNull() ?: return null
        if (!baseUrl.host.isNhentaiHost()) return null

        val absoluteMangaUrl = mangaUrl.toHttpUrlOrNull()
        if (absoluteMangaUrl != null && !absoluteMangaUrl.host.isNhentaiHost()) return null
        val mangaPath = absoluteMangaUrl?.encodedPath ?: mangaUrl.substringBefore('?').substringBefore('#')
        val galleryId = GALLERY_PATH.matchEntire(mangaPath)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val endpoint = baseUrl.resolve("/api/gallery/$galleryId/related") ?: return null
        return RequestTarget(httpSource, galleryId, endpoint.toString())
    }

    private fun parseSuccess(body: String, currentGalleryId: Long): NhentaiRelatedOutcome {
        if (body.isBlank() || body.trimStart().startsWith('<')) {
            return NhentaiRelatedOutcome.InvalidResponse(NhentaiInvalidResponseReason.NON_JSON)
        }

        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return NhentaiRelatedOutcome.InvalidResponse(NhentaiInvalidResponseReason.MALFORMED_JSON)

        val result = root["result"] as? JsonArray
            ?: return NhentaiRelatedOutcome.InvalidResponse(NhentaiInvalidResponseReason.MISSING_RESULT)
        val manga = result.mapNotNull { element ->
            (element as? JsonObject)?.toManga()
        }
            .filterNot { it.url == "/g/$currentGalleryId/" }
            .distinctBy(SManga::url)

        return NhentaiRelatedOutcome.Success(manga)
    }

    private fun JsonObject.toManga(): SManga? {
        val id = long("id") ?: return null
        val title = title() ?: return null
        val mediaId = string("media_id")
        val tags = (this["tags"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { tag ->
                val name = tag.string("name") ?: return@mapNotNull null
                NhentaiTag(type = tag.string("type").orEmpty().lowercase(), name = name)
            }

        val artists = tags.namesForTypes("artist")
        val authors = tags.namesForTypes("author")
        val groups = tags.namesForTypes("group")
        val genres = tags
            .filterNot { it.type in CREATOR_TAG_TYPES }
            .map(NhentaiTag::name)
            .distinctCaseInsensitive()
        val image = (this["images"] as? JsonObject)?.thumbnail(mediaId)

        return SManga.create().apply {
            url = "/g/$id/"
            this.title = title
            thumbnail_url = image
            artist = artists.joinOrNull()
            author = (authors + groups.map { "Circle: $it" }).distinctCaseInsensitive().joinOrNull()
            genre = genres.joinOrNull()
            status = SManga.COMPLETED
            initialized = false
        }
    }

    private fun JsonObject.title(): String? {
        return when (val value = this["title"]) {
            is JsonPrimitive -> value.contentOrNull.nonBlank()
            is JsonObject -> sequenceOf("pretty", "english", "japanese")
                .mapNotNull { name -> value.string(name) }
                .firstOrNull()
            else -> null
        }
    }

    private fun JsonObject.thumbnail(mediaId: String?): String? {
        mediaId ?: return null
        val thumbnailType = ((this["thumbnail"] as? JsonObject)?.get("t") as? JsonPrimitive)
            ?.contentOrNull
            .nonBlank()
        val coverType = ((this["cover"] as? JsonObject)?.get("t") as? JsonPrimitive)
            ?.contentOrNull
            .nonBlank()
        return when {
            thumbnailType != null -> "https://t.nhentai.net/galleries/$mediaId/thumb.${thumbnailType.imageExtension()}"
            coverType != null -> "https://t.nhentai.net/galleries/$mediaId/cover.${coverType.imageExtension()}"
            else -> null
        }
    }

    private fun JsonObject.string(name: String): String? {
        return (this[name] as? JsonPrimitive)?.contentOrNull.nonBlank()
    }

    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

    private fun List<NhentaiTag>.namesForTypes(vararg types: String): List<String> {
        return filter { it.type in types }.map(NhentaiTag::name).distinctCaseInsensitive()
    }

    private fun List<String>.distinctCaseInsensitive(): List<String> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(it.lowercase()) }
    }

    private fun List<String>.joinOrNull(): String? = takeIf(List<String>::isNotEmpty)?.joinToString(", ")

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String.imageExtension(): String {
        return when (lowercase()) {
            "j", "jpg", "jpeg" -> "jpg"
            "p", "png" -> "png"
            "g", "gif" -> "gif"
            "w", "webp" -> "webp"
            else -> "jpg"
        }
    }

    private fun String.isNhentaiHost(): Boolean {
        val normalized = lowercase().trimEnd('.')
        return normalized == NHENTAI_HOST || normalized.endsWith(".$NHENTAI_HOST")
    }

    private fun classifyHttpFailure(statusCode: Int): NhentaiHttpFailureClassification {
        return when (statusCode) {
            404, 405, 410 -> NhentaiHttpFailureClassification.CAPABILITY_UNAVAILABLE
            // A 403 is commonly a temporary anti-bot challenge, not proof that the endpoint is gone.
            403, 408, 425, in 500..599 -> NhentaiHttpFailureClassification.TRANSIENT
            else -> NhentaiHttpFailureClassification.OTHER
        }
    }

    private fun parseRetryAfter(value: String?): Duration? {
        value?.trim()?.toLongOrNull()?.let { seconds ->
            return seconds.coerceAtLeast(0).seconds
        }
        val retryEpochMillis = try {
            value?.let { Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(it.trim())).toEpochMilli() }
        } catch (_: DateTimeParseException) {
            null
        }
        return retryEpochMillis?.minus(nowEpochMillis())?.coerceAtLeast(0)?.milliseconds
    }

    private data class RequestTarget(
        val source: HttpSource,
        val galleryId: Long,
        val endpoint: String,
    ) : NhentaiRelatedTarget {
        override val sourceId: Long = source.id
        override val hostKey: String = NHENTAI_HOST
        override val cacheKey: String = "$sourceId:$galleryId"
    }

    private data class NhentaiTag(
        val type: String,
        val name: String,
    )

    private companion object {
        val GALLERY_PATH = Regex("^/g/([0-9]+)/?$")
        val CREATOR_TAG_TYPES = setOf("artist", "author", "group")
        const val NHENTAI_HOST = "nhentai.net"
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

internal sealed interface NhentaiRelatedOutcome {
    data class Success(val manga: List<SManga>) : NhentaiRelatedOutcome

    data object Unsupported : NhentaiRelatedOutcome

    data class RateLimited(val retryAfter: Duration?) : NhentaiRelatedOutcome

    data class HttpFailure(
        val statusCode: Int,
        val classification: NhentaiHttpFailureClassification,
    ) : NhentaiRelatedOutcome

    data class InvalidResponse(val reason: NhentaiInvalidResponseReason) : NhentaiRelatedOutcome

    data class NetworkFailure(val cause: IOException) : NhentaiRelatedOutcome
}

internal enum class NhentaiHttpFailureClassification {
    CAPABILITY_UNAVAILABLE,
    TRANSIENT,
    OTHER,
}

internal enum class NhentaiInvalidResponseReason {
    NON_JSON,
    MALFORMED_JSON,
    MISSING_RESULT,
}
