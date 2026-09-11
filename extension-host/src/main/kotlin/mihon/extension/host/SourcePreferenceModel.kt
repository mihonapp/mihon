package mihon.extension.host

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceOptionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.source.WindowsSource

/**
 * Returns the [ConfigurableSource] behind [this] source, if any.
 *
 * Tachiyomi extensions are wrapped in [TachiyomiCatalogueSourceAdapter], so the preference bridge
 * needs to unwrap the delegate before checking the marker interface.
 */
internal fun WindowsSource.configurableSourceOrNull(): ConfigurableSource? = when (this) {
    is ConfigurableSource -> this
    is TachiyomiCatalogueSourceAdapter -> delegate as? ConfigurableSource
    else -> null
}

/**
 * Owns the `PreferenceScreen` built by one source's `setupPreferenceScreen` call.
 *
 * The screen is created once and kept for the lifetime of the host so:
 *  - subsequent get requests are cheap,
 *  - set requests can be applied to the exact `Preference` instances (including their
 *    `OnPreferenceChangeListener`s) instead of only writing to `SharedPreferences`.
 */
internal class SourcePreferenceModel(
    private val source: ConfigurableSource,
    private val context: Context,
) {
    private val preferences: SharedPreferences = source.getSourcePreferences()
    private val screen: PreferenceScreen = PreferenceScreen(context).also { source.setupPreferenceScreen(it) }

    private val preferencesByKey: Map<String, Preference> = buildList {
        for (index in 0 until screen.getPreferenceCount()) {
            screen.getPreference(index)?.let { collect(it, this) }
        }
    }
        .filter { it.key.isNotBlank() }
        .associateBy { it.key }

    fun definitions(): List<SourcePreferenceDefinitionDto> {
        val ordered = buildList {
            for (index in 0 until screen.getPreferenceCount()) {
                screen.getPreference(index)?.let { collect(it, this) }
            }
        }
        return ordered.mapNotNull { preference ->
            if (preference.key.isBlank()) null else preference.toDefinition()
        }
    }

    fun set(key: String, incoming: SourcePreferenceValueDto): SourcePreferenceValueDto {
        val preference = preferencesByKey[key]
            ?: throw IllegalArgumentException("Unknown source preference '$key'")
        val definition = preference.toDefinition()
        if (definition.readOnly || definition.type == SourcePreferenceTypeDto.UNKNOWN) {
            throw IllegalArgumentException("Source preference '$key' is read-only")
        }

        val newValue = convertValue(definition.type, incoming)
        val listener = preference.onPreferenceChangeListener
        if (listener != null && !listener.onPreferenceChange(preference, newValue)) {
            throw IllegalArgumentException("Source preference '$key' rejected the new value")
        }

        persist(definition.type, key, newValue)
        applyToPreference(preference, definition.type, newValue)

        return preference.toDefinition().currentValue
            ?: throw IllegalStateException("Source preference '$key' has no current value after update")
    }

    private fun collect(preference: Preference, target: MutableList<Preference>) {
        target += preference
        if (preference is PreferenceGroup) {
            for (index in 0 until preference.getPreferenceCount()) {
                preference.getPreference(index)?.let { collect(it, target) }
            }
        }
    }

    private fun Preference.toDefinition(): SourcePreferenceDefinitionDto {
        val key = key
        val title = title?.toString().orEmpty().ifBlank { key }
        val summary = summary?.toString().orEmpty()

        return when (this) {
            is CheckBoxPreference -> booleanDefinition(this, key, title, summary, isChecked)
            is SwitchPreferenceCompat -> booleanDefinition(this, key, title, summary, isChecked)
            is ListPreference -> selectDefinition(this, key, title, summary)
            is MultiSelectListPreference -> listDefinition(this, key, title, summary)
            is EditTextPreference -> editTextDefinition(key, title, summary)
            else -> unknownDefinition(key, title, summary)
        }
    }

    private fun booleanDefinition(
        preference: Preference,
        key: String,
        title: String,
        summary: String,
        checked: Boolean,
    ): SourcePreferenceDefinitionDto {
        val defaultValue = when (val rawDefault = preference.defaultValue) {
            is Boolean -> rawDefault
            is String -> rawDefault.toBooleanStrictOrNull() ?: checked
            else -> checked
        }
        val currentValue = if (preferences.contains(key)) {
            preferences.getBoolean(key, defaultValue)
        } else {
            checked
        }
        return SourcePreferenceDefinitionDto(
            key = key,
            title = title,
            summary = summary,
            type = SourcePreferenceTypeDto.BOOLEAN,
            defaultValue = BooleanPreferenceValueDto(defaultValue),
            currentValue = BooleanPreferenceValueDto(currentValue),
        )
    }

    private fun selectDefinition(
        preference: ListPreference,
        key: String,
        title: String,
        summary: String,
    ): SourcePreferenceDefinitionDto {
        val options = optionsOf(preference.entries, preference.entryValues)
        val defaultValue = (preference.defaultValue as? String)
            ?: preference.defaultValue?.toString()
            ?: preference.value
            ?: options.firstOrNull()?.value
            ?: ""
        val currentValue = if (preferences.contains(key)) {
            preferences.getString(key, defaultValue) ?: defaultValue
        } else {
            preference.value ?: defaultValue
        }
        return SourcePreferenceDefinitionDto(
            key = key,
            title = title,
            summary = summary,
            type = SourcePreferenceTypeDto.SELECT,
            defaultValue = StringPreferenceValueDto(defaultValue),
            currentValue = SelectPreferenceValueDto(currentValue),
            options = options,
        )
    }

    private fun listDefinition(
        preference: MultiSelectListPreference,
        key: String,
        title: String,
        summary: String,
    ): SourcePreferenceDefinitionDto {
        val options = optionsOf(preference.entries, preference.entryValues)
        val rawDefaultValues = (preference.defaultValue as? Collection<*>)
            ?.mapNotNull { it?.toString() }
            ?: preference.values.toList()
        val defaultValues = orderValues(rawDefaultValues, options)
        val rawCurrentValues = if (preferences.contains(key)) {
            preferences.getStringSet(key, defaultValues.toSet())?.toList() ?: defaultValues
        } else {
            preference.values.toList()
        }
        val currentValues = orderValues(rawCurrentValues, options)
        return SourcePreferenceDefinitionDto(
            key = key,
            title = title,
            summary = summary,
            type = SourcePreferenceTypeDto.LIST,
            defaultValue = ListPreferenceValueDto(defaultValues),
            currentValue = ListPreferenceValueDto(currentValues),
            options = options,
        )
    }

    private fun editTextDefinition(
        key: String,
        title: String,
        summary: String,
    ): SourcePreferenceDefinitionDto {
        val preference = preferencesByKey[key] as? EditTextPreference
        val type = editTextType(key, preference)
        val defaultValue = defaultEditTextValue(type, preference)
        val currentValue = when (type) {
            SourcePreferenceTypeDto.INT -> IntPreferenceValueDto(
                preferences.getInt(key, defaultValue as Int),
            )
            SourcePreferenceTypeDto.LONG -> LongPreferenceValueDto(
                preferences.getLong(key, defaultValue as Long),
            )
            SourcePreferenceTypeDto.FLOAT -> FloatPreferenceValueDto(
                preferences.getFloat(key, defaultValue as Float),
            )
            else -> StringPreferenceValueDto(
                preferences.getString(key, defaultValue as String) ?: "",
            )
        }
        return SourcePreferenceDefinitionDto(
            key = key,
            title = title,
            summary = summary,
            type = type,
            defaultValue = when (defaultValue) {
                is Int -> IntPreferenceValueDto(defaultValue)
                is Long -> LongPreferenceValueDto(defaultValue)
                is Float -> FloatPreferenceValueDto(defaultValue)
                else -> StringPreferenceValueDto(defaultValue.toString())
            },
            currentValue = currentValue,
        )
    }

    private fun unknownDefinition(
        key: String,
        title: String,
        summary: String,
    ): SourcePreferenceDefinitionDto {
        val defaultRaw = runCatching { preferences.getAll()[key]?.toString() }.getOrNull()
            ?: preferencesByKey[key]?.defaultValue?.toString()
        val currentRaw = runCatching { preferences.getAll()[key]?.toString() }.getOrNull() ?: defaultRaw
        return SourcePreferenceDefinitionDto(
            key = key,
            title = title,
            summary = summary,
            type = SourcePreferenceTypeDto.UNKNOWN,
            defaultValue = UnknownPreferenceValueDto(defaultRaw),
            currentValue = UnknownPreferenceValueDto(currentRaw),
            readOnly = true,
        )
    }

    private fun editTextType(key: String, preference: EditTextPreference?): SourcePreferenceTypeDto {
        val default = preference?.defaultValue
        val stored = runCatching { preferences.getAll()[key] }.getOrNull()
        return when {
            default is Int || default is Short || default is Byte -> SourcePreferenceTypeDto.INT
            default is Long -> SourcePreferenceTypeDto.LONG
            default is Float || default is Double -> SourcePreferenceTypeDto.FLOAT
            stored is Int || stored is Short || stored is Byte -> SourcePreferenceTypeDto.INT
            stored is Long -> SourcePreferenceTypeDto.LONG
            stored is Float || stored is Double -> SourcePreferenceTypeDto.FLOAT
            default == null && stored == null -> inferTextType(preference?.text)
            else -> SourcePreferenceTypeDto.STRING
        }
    }

    private fun inferTextType(text: String?): SourcePreferenceTypeDto {
        if (text.isNullOrBlank()) return SourcePreferenceTypeDto.STRING
        return when {
            text.toIntOrNull() != null -> SourcePreferenceTypeDto.INT
            text.toLongOrNull() != null -> SourcePreferenceTypeDto.LONG
            text.toFloatOrNull() != null -> SourcePreferenceTypeDto.FLOAT
            else -> SourcePreferenceTypeDto.STRING
        }
    }

    private fun defaultEditTextValue(
        type: SourcePreferenceTypeDto,
        preference: EditTextPreference?,
    ): Any {
        val default = preference?.defaultValue
        val text = preference?.text
        return when (type) {
            SourcePreferenceTypeDto.INT -> when (default) {
                is Number -> default.toInt()
                else -> text?.toIntOrNull() ?: 0
            }
            SourcePreferenceTypeDto.LONG -> when (default) {
                is Number -> default.toLong()
                else -> text?.toLongOrNull() ?: 0L
            }
            SourcePreferenceTypeDto.FLOAT -> when (default) {
                is Number -> default.toFloat()
                else -> text?.toFloatOrNull() ?: 0f
            }
            else -> default?.toString() ?: text ?: ""
        }
    }

    private fun optionsOf(
        entries: Array<CharSequence>?,
        entryValues: Array<CharSequence>?,
    ): List<SourcePreferenceOptionDto> {
        val labels = entries?.map { it.toString() }.orEmpty()
        val values = entryValues?.map { it.toString() } ?: labels
        if (labels.isEmpty() || labels.size != values.size) return emptyList()
        return labels.zip(values).map { (label, value) -> SourcePreferenceOptionDto(label, value) }
    }

    private fun orderValues(values: List<String>, options: List<SourcePreferenceOptionDto>): List<String> {
        if (options.isEmpty()) return values
        val selected = values.toSet()
        val ordered = options.map { it.value }.filter { it in selected }
        val extras = values.filter { value -> options.none { it.value == value } }
        return ordered + extras
    }

    private fun convertValue(type: SourcePreferenceTypeDto, incoming: SourcePreferenceValueDto): Any {
        return when (type) {
            SourcePreferenceTypeDto.BOOLEAN -> when (incoming) {
                is BooleanPreferenceValueDto -> incoming.value
                is StringPreferenceValueDto -> incoming.value.toBooleanStrictOrNull()
                    ?: throw IllegalArgumentException("Expected a boolean source preference value")
                else -> throw IllegalArgumentException("Expected a boolean source preference value")
            }
            SourcePreferenceTypeDto.STRING -> when (incoming) {
                is StringPreferenceValueDto -> incoming.value
                is SelectPreferenceValueDto -> incoming.value
                is IntPreferenceValueDto -> incoming.value.toString()
                is LongPreferenceValueDto -> incoming.value.toString()
                is FloatPreferenceValueDto -> incoming.value.toString()
                is BooleanPreferenceValueDto -> incoming.value.toString()
                is UnknownPreferenceValueDto -> incoming.value.orEmpty()
                else -> throw IllegalArgumentException("Expected a string source preference value")
            }
            SourcePreferenceTypeDto.INT -> when (incoming) {
                is IntPreferenceValueDto -> incoming.value
                is LongPreferenceValueDto -> incoming.value.toInt()
                is FloatPreferenceValueDto -> incoming.value.toInt()
                is StringPreferenceValueDto -> incoming.value.toIntOrNull()
                    ?: throw IllegalArgumentException("Expected an int source preference value")
                else -> throw IllegalArgumentException("Expected an int source preference value")
            }
            SourcePreferenceTypeDto.LONG -> when (incoming) {
                is LongPreferenceValueDto -> incoming.value
                is IntPreferenceValueDto -> incoming.value.toLong()
                is FloatPreferenceValueDto -> incoming.value.toLong()
                is StringPreferenceValueDto -> incoming.value.toLongOrNull()
                    ?: throw IllegalArgumentException("Expected a long source preference value")
                else -> throw IllegalArgumentException("Expected a long source preference value")
            }
            SourcePreferenceTypeDto.FLOAT -> when (incoming) {
                is FloatPreferenceValueDto -> incoming.value
                is IntPreferenceValueDto -> incoming.value.toFloat()
                is LongPreferenceValueDto -> incoming.value.toFloat()
                is StringPreferenceValueDto -> incoming.value.toFloatOrNull()
                    ?: throw IllegalArgumentException("Expected a float source preference value")
                else -> throw IllegalArgumentException("Expected a float source preference value")
            }
            SourcePreferenceTypeDto.SELECT -> when (incoming) {
                is SelectPreferenceValueDto -> incoming.value
                is StringPreferenceValueDto -> incoming.value
                is IntPreferenceValueDto -> incoming.value.toString()
                is LongPreferenceValueDto -> incoming.value.toString()
                is FloatPreferenceValueDto -> incoming.value.toString()
                is ListPreferenceValueDto -> incoming.value.firstOrNull().orEmpty()
                else -> throw IllegalArgumentException("Expected a select source preference value")
            }
            SourcePreferenceTypeDto.LIST -> when (incoming) {
                is ListPreferenceValueDto -> incoming.value.toSet()
                is StringPreferenceValueDto -> incoming.value.split(',').map {
                    it.trim()
                }.filter { it.isNotEmpty() }.toSet()
                is SelectPreferenceValueDto -> setOf(incoming.value)
                else -> throw IllegalArgumentException("Expected a list source preference value")
            }
            SourcePreferenceTypeDto.UNKNOWN ->
                throw IllegalArgumentException("Unknown source preference types are read-only")
        }
    }

    private fun persist(type: SourcePreferenceTypeDto, key: String, value: Any) {
        if (key.isBlank()) throw IllegalArgumentException("Cannot persist a source preference without a key")
        val editor = preferences.edit()
        when (type) {
            SourcePreferenceTypeDto.BOOLEAN -> editor.putBoolean(key, value as Boolean)
            SourcePreferenceTypeDto.STRING, SourcePreferenceTypeDto.SELECT -> editor.putString(key, value.toString())
            SourcePreferenceTypeDto.INT -> editor.putInt(key, value as Int)
            SourcePreferenceTypeDto.LONG -> editor.putLong(key, value as Long)
            SourcePreferenceTypeDto.FLOAT -> editor.putFloat(key, value as Float)
            SourcePreferenceTypeDto.LIST -> {
                @Suppress("UNCHECKED_CAST")
                editor.putStringSet(key, value as Set<String>)
            }
            SourcePreferenceTypeDto.UNKNOWN -> throw IllegalArgumentException(
                "Unknown source preference types are read-only",
            )
        }
        editor.apply()
    }

    private fun applyToPreference(preference: Preference, type: SourcePreferenceTypeDto, value: Any) {
        when (preference) {
            is CheckBoxPreference -> preference.isChecked = value as Boolean
            is SwitchPreferenceCompat -> preference.isChecked = value as Boolean
            is EditTextPreference -> preference.text = value.toString()
            is ListPreference -> preference.value = value as String
            is MultiSelectListPreference -> {
                @Suppress("UNCHECKED_CAST")
                preference.values = (value as Set<String>)
            }
            else -> {
                // Unknown/custom preferences are read-only and never reach this branch.
                if (type != SourcePreferenceTypeDto.UNKNOWN) {
                    throw IllegalArgumentException("Unsupported source preference class ${preference::class.java.name}")
                }
            }
        }
    }
}
