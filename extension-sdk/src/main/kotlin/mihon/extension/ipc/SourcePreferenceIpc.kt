package mihon.extension.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializable IPC representation of the preference model exposed by a Tachiyomi
 * `ConfigurableSource`.
 *
 * Android's preference framework is UI-centric: a source populates a `PreferenceScreen` with
 * `Preference` instances and reads/writes the persisted values through `SharedPreferences`. These
 * DTOs carry the definition metadata (type, key, title, summary, default/current value and, for
 * list/select preferences, the available options) between the extension host and the desktop app.
 *
 * Unknown preference types are represented by [SourcePreferenceTypeDto.UNKNOWN] and are kept in the
 * list as read-only entries instead of being silently dropped.
 */
@Serializable
enum class SourcePreferenceTypeDto {
    @SerialName("boolean")
    BOOLEAN,

    @SerialName("string")
    STRING,

    @SerialName("int")
    INT,

    @SerialName("float")
    FLOAT,

    @SerialName("long")
    LONG,

    @SerialName("select")
    SELECT,

    @SerialName("list")
    LIST,

    @SerialName("unknown")
    UNKNOWN,
}

/** A single option of a list/select preference. [value] is what gets persisted. */
@Serializable
data class SourcePreferenceOptionDto(
    val label: String,
    val value: String,
)

/** Typed preference value used by get/set source preference IPC commands. */
@Serializable
sealed interface SourcePreferenceValueDto

@Serializable
@SerialName("boolean")
data class BooleanPreferenceValueDto(val value: Boolean) : SourcePreferenceValueDto

@Serializable
@SerialName("string")
data class StringPreferenceValueDto(val value: String) : SourcePreferenceValueDto

@Serializable
@SerialName("int")
data class IntPreferenceValueDto(val value: Int) : SourcePreferenceValueDto

@Serializable
@SerialName("float")
data class FloatPreferenceValueDto(val value: Float) : SourcePreferenceValueDto

@Serializable
@SerialName("long")
data class LongPreferenceValueDto(val value: Long) : SourcePreferenceValueDto

@Serializable
@SerialName("select")
data class SelectPreferenceValueDto(val value: String) : SourcePreferenceValueDto

@Serializable
@SerialName("list")
data class ListPreferenceValueDto(val value: List<String> = emptyList()) : SourcePreferenceValueDto

@Serializable
@SerialName("unknown")
data class UnknownPreferenceValueDto(val value: String? = null) : SourcePreferenceValueDto

/** A complete definition of one source preference, including its current value. */
@Serializable
data class SourcePreferenceDefinitionDto(
    val key: String,
    val title: String = "",
    val summary: String = "",
    val type: SourcePreferenceTypeDto = SourcePreferenceTypeDto.UNKNOWN,
    val defaultValue: SourcePreferenceValueDto? = null,
    val currentValue: SourcePreferenceValueDto? = null,
    val options: List<SourcePreferenceOptionDto> = emptyList(),
    val readOnly: Boolean = false,
)

/** Result of a `get_source_preferences` request. */
@Serializable
data class SourcePreferencesDto(
    val sourceId: Long = 0L,
    val supported: Boolean = false,
    val definitions: List<SourcePreferenceDefinitionDto> = emptyList(),
)

/** Payload of a `set_source_preference` request. */
@Serializable
data class SetSourcePreferencePayload(
    val sourceId: Long,
    val key: String,
    val value: SourcePreferenceValueDto,
)

private val sourcePreferenceIpcJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun encodeSourcePreferences(preferences: SourcePreferencesDto): String =
    sourcePreferenceIpcJson.encodeToString(preferences)

fun decodeSourcePreferences(payload: String?): SourcePreferencesDto? {
    if (payload.isNullOrBlank()) return null
    return try {
        sourcePreferenceIpcJson.decodeFromString<SourcePreferencesDto>(payload)
    } catch (_: Exception) {
        null
    }
}

fun encodeSourcePreferenceValue(value: SourcePreferenceValueDto): String =
    sourcePreferenceIpcJson.encodeToString(value)

fun decodeSourcePreferenceValue(payload: String?): SourcePreferenceValueDto? {
    if (payload.isNullOrBlank()) return null
    return try {
        sourcePreferenceIpcJson.decodeFromString<SourcePreferenceValueDto>(payload)
    } catch (_: Exception) {
        null
    }
}

// Compatibility aliases for callers that use the shorter DTO names.
typealias SourcePreferenceType = SourcePreferenceTypeDto
typealias SourcePreferenceOption = SourcePreferenceOptionDto
typealias SourcePreferenceValue = SourcePreferenceValueDto
typealias SourcePreferenceDefinition = SourcePreferenceDefinitionDto
typealias SourcePreferences = SourcePreferencesDto
typealias GetSourcePreferencesPayload = SourcePayload
