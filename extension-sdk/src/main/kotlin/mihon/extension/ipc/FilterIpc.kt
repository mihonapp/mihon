package mihon.extension.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList

/**
 * Serializable IPC representation of [Filter].
 *
 * The extension SDK [Filter] model is mutable and contains arrays / generic values, which cannot be
 * serialized directly. These DTOs capture the definition and current state of every supported filter
 * type so the main process and the extension host can exchange a [FilterList] without sharing class
 * instances.
 *
 * [SelectFilterDto.values] and [SortFilterDto.values] are stored as strings because filter values
 * are only used for display in the host UI; the authoritative state is the selected index. This keeps
 * the payload stable even when a source uses a custom value type.
 */
@Serializable
sealed interface FilterDto {
    val name: String
}

@Serializable
@SerialName("header")
data class HeaderFilterDto(override val name: String) : FilterDto

@Serializable
@SerialName("separator")
data class SeparatorFilterDto(override val name: String = "") : FilterDto

@Serializable
@SerialName("text")
data class TextFilterDto(
    override val name: String,
    val state: String = "",
) : FilterDto

@Serializable
@SerialName("checkbox")
data class CheckBoxFilterDto(
    override val name: String,
    val state: Boolean = false,
) : FilterDto

@Serializable
@SerialName("select")
data class SelectFilterDto(
    override val name: String,
    val values: List<String> = emptyList(),
    val state: Int = 0,
) : FilterDto

@Serializable
@SerialName("tristate")
data class TriStateFilterDto(
    override val name: String,
    val state: Int = Filter.TriState.STATE_IGNORE,
) : FilterDto

@Serializable
@SerialName("group")
data class GroupFilterDto(
    override val name: String,
    val filters: List<FilterDto> = emptyList(),
) : FilterDto

@Serializable
@SerialName("sort")
data class SortFilterDto(
    override val name: String,
    val values: List<String> = emptyList(),
    val state: SortSelectionDto? = null,
) : FilterDto

@Serializable
data class SortSelectionDto(
    val index: Int,
    val ascending: Boolean,
)

@Serializable
data class FilterListDto(
    val filters: List<FilterDto> = emptyList(),
)

private val filterIpcJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    classDiscriminator = "type"
}

fun FilterList.toFilterListDto(): FilterListDto = FilterListDto(filters.map { it.toFilterDto() })

fun Filter<*>.toFilterDto(): FilterDto = when (this) {
    is Filter.Header -> HeaderFilterDto(name)
    is Filter.Separator -> SeparatorFilterDto(name)
    is Filter.Text -> TextFilterDto(name, state)
    is Filter.CheckBox -> CheckBoxFilterDto(name, state)
    is Filter.Select<*> -> SelectFilterDto(name, values.map { it?.toString() ?: "" }, state)
    is Filter.TriState -> TriStateFilterDto(name, state)
    is Filter.Group<*> -> GroupFilterDto(
        name = name,
        filters = state.mapNotNull { child -> (child as? Filter<*>)?.toFilterDto() },
    )
    is Filter.Sort -> SortFilterDto(
        name = name,
        values = values.toList(),
        state = state?.let { SortSelectionDto(it.index, it.ascending) },
    )
}

fun FilterListDto.toFilterList(): FilterList = FilterList(filters.map { it.toFilter() })

fun FilterDto.toFilter(): Filter<*> = when (this) {
    is HeaderFilterDto -> Filter.Header(name)
    is SeparatorFilterDto -> Filter.Separator(name)
    is TextFilterDto -> Filter.Text(name, state)
    is CheckBoxFilterDto -> Filter.CheckBox(name, state)
    is SelectFilterDto -> Filter.Select(name, values.toTypedArray(), state)
    is TriStateFilterDto -> Filter.TriState(name, state)
    is GroupFilterDto -> Filter.Group(name, filters.map { it.toFilter() })
    is SortFilterDto -> Filter.Sort(
        name,
        values.toTypedArray(),
        state?.let { Filter.Sort.Selection(it.index, it.ascending) },
    )
}

/**
 * Copies state from [other] onto this filter list, matching filters by name.
 *
 * This lets the host apply the state selected in the desktop UI to the source's own freshly created
 * filter instances. Keeping the source's instances (and custom [Filter] subclasses) preserves any
 * behavior implemented by those filters, instead of passing reconstructed base DTO filters to the
 * source.
 */
fun FilterList.applyStateFrom(other: FilterList): FilterList {
    val incomingByName = other.filters.associateBy { it.name }
    filters.forEach { target ->
        val incoming = incomingByName[target.name] ?: return@forEach
        target.applyStateFrom(incoming)
    }
    return this
}

fun Filter<*>.applyStateFrom(other: Filter<*>): Filter<*> {
    when {
        this is Filter.Text && other is Filter.Text -> state = other.state
        this is Filter.CheckBox && other is Filter.CheckBox -> state = other.state
        this is Filter.Select<*> && other is Filter.Select<*> -> state = other.state
        this is Filter.TriState && other is Filter.TriState -> state = other.state
        this is Filter.Sort && other is Filter.Sort -> {
            val selection = other.state
            state = selection?.let { Filter.Sort.Selection(it.index, it.ascending) }
        }
        this is Filter.Group<*> && other is Filter.Group<*> -> {
            val incomingChildren = other.state.filterIsInstance<Filter<*>>().associateBy { it.name }
            state.filterIsInstance<Filter<*>>().forEach { child ->
                incomingChildren[child.name]?.let { child.applyStateFrom(it) }
            }
        }
    }
    return this
}

fun encodeFilterList(filterList: FilterList): String = filterIpcJson.encodeToString(filterList.toFilterListDto())

/**
 * Decodes a filter list payload. Blank or malformed payloads are treated as "no filters" so older
 * clients / hosts that did not send filters keep working.
 */
fun decodeFilterList(filtersJson: String?): FilterList {
    if (filtersJson.isNullOrBlank()) return FilterList()
    return try {
        filterIpcJson.decodeFromString<FilterListDto>(filtersJson).toFilterList()
    } catch (_: Exception) {
        FilterList()
    }
}
