package mihon.extension.ipc

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourcePreferenceIpcTest {

    private val allValues = listOf(
        BooleanPreferenceValueDto(true),
        StringPreferenceValueDto("value"),
        IntPreferenceValueDto(42),
        FloatPreferenceValueDto(1.5f),
        LongPreferenceValueDto(9_000_000_000L),
        SelectPreferenceValueDto("high"),
        ListPreferenceValueDto(listOf("action", "comedy")),
        UnknownPreferenceValueDto("raw"),
        UnknownPreferenceValueDto(null),
    )

    @Test
    fun `round trips every supported source preference value type`() {
        allValues.forEach { value ->
            decodeSourcePreferenceValue(encodeSourcePreferenceValue(value)) shouldBe value
        }
    }

    @Test
    fun `round trips source preference definitions for all types`() {
        val definitions = listOf(
            SourcePreferenceDefinitionDto(
                key = "dataSaver",
                title = "Data Saver",
                summary = "Reduce data usage",
                type = SourcePreferenceTypeDto.BOOLEAN,
                defaultValue = BooleanPreferenceValueDto(false),
                currentValue = BooleanPreferenceValueDto(true),
            ),
            SourcePreferenceDefinitionDto(
                key = "apiKey",
                title = "API Key",
                type = SourcePreferenceTypeDto.STRING,
                defaultValue = StringPreferenceValueDto(""),
                currentValue = StringPreferenceValueDto("secret"),
            ),
            SourcePreferenceDefinitionDto(
                key = "pageLimit",
                title = "Page Limit",
                type = SourcePreferenceTypeDto.INT,
                defaultValue = IntPreferenceValueDto(20),
                currentValue = IntPreferenceValueDto(50),
            ),
            SourcePreferenceDefinitionDto(
                key = "threshold",
                title = "Threshold",
                type = SourcePreferenceTypeDto.FLOAT,
                defaultValue = FloatPreferenceValueDto(1.5f),
                currentValue = FloatPreferenceValueDto(2.5f),
            ),
            SourcePreferenceDefinitionDto(
                key = "timeout",
                title = "Timeout",
                type = SourcePreferenceTypeDto.LONG,
                defaultValue = LongPreferenceValueDto(1_000L),
                currentValue = LongPreferenceValueDto(5_000L),
            ),
            SourcePreferenceDefinitionDto(
                key = "quality",
                title = "Quality",
                type = SourcePreferenceTypeDto.SELECT,
                defaultValue = SelectPreferenceValueDto("low"),
                currentValue = SelectPreferenceValueDto("high"),
                options = listOf(
                    SourcePreferenceOptionDto("Low", "low"),
                    SourcePreferenceOptionDto("High", "high"),
                ),
            ),
            SourcePreferenceDefinitionDto(
                key = "genres",
                title = "Genres",
                type = SourcePreferenceTypeDto.LIST,
                defaultValue = ListPreferenceValueDto(listOf("action")),
                currentValue = ListPreferenceValueDto(listOf("action", "comedy")),
                options = listOf(
                    SourcePreferenceOptionDto("Action", "action"),
                    SourcePreferenceOptionDto("Comedy", "comedy"),
                ),
            ),
            SourcePreferenceDefinitionDto(
                key = "custom",
                title = "Custom",
                type = SourcePreferenceTypeDto.UNKNOWN,
                defaultValue = UnknownPreferenceValueDto("default"),
                currentValue = UnknownPreferenceValueDto("current"),
                readOnly = true,
            ),
        )
        val sourcePreferences = SourcePreferencesDto(
            sourceId = 4242L,
            supported = true,
            definitions = definitions,
        )

        val decoded = decodeSourcePreferences(encodeSourcePreferences(sourcePreferences))
        decoded shouldBe sourcePreferences
    }

    @Test
    fun `decode helpers tolerate blank and malformed payloads`() {
        decodeSourcePreferences("") shouldBe null
        decodeSourcePreferences("not json") shouldBe null
        decodeSourcePreferenceValue("") shouldBe null
        decodeSourcePreferenceValue("{}") shouldBe null
    }
}
