package mihon.extension.ipc

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import org.junit.jupiter.api.Test

class ExtensionFilterIpcTest {

    private fun roundTrip(filters: FilterList): FilterList {
        return decodeFilterList(encodeFilterList(filters))
    }

    @Test
    fun `round trips every filter type with selected state`() {
        val original = FilterList(
            Filter.Header("Header"),
            Filter.Separator("Separator"),
            Filter.Text("Author", "Eiichiro Oda"),
            Filter.CheckBox("Completed", true),
            Filter.Select("Status", arrayOf("Any", "Ongoing", "Completed"), 2),
            Filter.TriState("Genre", Filter.TriState.STATE_EXCLUDE),
            Filter.Group(
                "Group",
                listOf(
                    Filter.CheckBox("Action", true),
                    Filter.TriState("Romance", Filter.TriState.STATE_INCLUDE),
                ),
            ),
            Filter.Sort("Sort", arrayOf("Title", "Date"), Filter.Sort.Selection(1, true)),
        )

        val restored = roundTrip(original)

        restored.filters.size shouldBe 8

        restored.filters[0].shouldBeInstanceOf<Filter.Header>().name shouldBe "Header"
        restored.filters[1].shouldBeInstanceOf<Filter.Separator>().name shouldBe "Separator"

        val text = restored.filters[2].shouldBeInstanceOf<Filter.Text>()
        text.name shouldBe "Author"
        text.state shouldBe "Eiichiro Oda"

        val checkBox = restored.filters[3].shouldBeInstanceOf<Filter.CheckBox>()
        checkBox.name shouldBe "Completed"
        checkBox.state shouldBe true

        val select = restored.filters[4].shouldBeInstanceOf<Filter.Select<*>>()
        select.name shouldBe "Status"
        select.values.toList() shouldBe listOf("Any", "Ongoing", "Completed")
        select.state shouldBe 2

        val triState = restored.filters[5].shouldBeInstanceOf<Filter.TriState>()
        triState.state shouldBe Filter.TriState.STATE_EXCLUDE

        val group = restored.filters[6].shouldBeInstanceOf<Filter.Group<*>>()
        group.name shouldBe "Group"
        group.state.size shouldBe 2
        group.state[0].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe true
        group.state[1].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_INCLUDE

        val sort = restored.filters[7].shouldBeInstanceOf<Filter.Sort>()
        sort.name shouldBe "Sort"
        sort.values.toList() shouldBe listOf("Title", "Date")
        sort.state shouldBe Filter.Sort.Selection(1, true)
    }

    @Test
    fun `round trips default states for each filter type`() {
        val original = FilterList(
            Filter.Header("Header"),
            Filter.Separator(),
            Filter.Text("Text"),
            Filter.CheckBox("CheckBox"),
            Filter.Select("Select", arrayOf("A", "B")),
            Filter.TriState("TriState"),
            Filter.Group("Group", listOf(Filter.CheckBox("Nested"))),
            Filter.Sort("Sort", arrayOf("A", "B")),
        )

        val restored = roundTrip(original)

        restored.filters[2].shouldBeInstanceOf<Filter.Text>().state shouldBe ""
        restored.filters[3].shouldBeInstanceOf<Filter.CheckBox>().state shouldBe false
        restored.filters[4].shouldBeInstanceOf<Filter.Select<*>>().state shouldBe 0
        restored.filters[5].shouldBeInstanceOf<Filter.TriState>().state shouldBe Filter.TriState.STATE_IGNORE
        restored.filters[6].shouldBeInstanceOf<Filter.Group<*>>().state
            .single()
            .shouldBeInstanceOf<Filter.CheckBox>()
            .state shouldBe false
        restored.filters[7].shouldBeInstanceOf<Filter.Sort>().state shouldBe null
    }

    @Test
    fun `serialized dto keeps discriminator and nested group structure`() {
        val dto = FilterList(
            Filter.Header("Header"),
            Filter.Group("Group", listOf(Filter.CheckBox("Nested", true))),
        ).toFilterListDto()

        val encoded = Json.encodeToString(dto)

        encoded shouldContain "\"type\":\"header\""
        encoded shouldContain "\"type\":\"group\""
        encoded shouldContain "\"type\":\"checkbox\""
        encoded shouldContain "\"Nested\""
    }

    @Test
    fun `blank missing and malformed payloads decode to empty filter list`() {
        decodeFilterList(null).isEmpty() shouldBe true
        decodeFilterList("").isEmpty() shouldBe true
        decodeFilterList("   ").isEmpty() shouldBe true
        decodeFilterList("not-json").isEmpty() shouldBe true
        decodeFilterList("""{"filters":[]}""").isEmpty() shouldBe true
    }
}
