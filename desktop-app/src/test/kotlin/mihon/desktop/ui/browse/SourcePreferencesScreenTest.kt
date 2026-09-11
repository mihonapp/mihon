package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.SourcePreferenceDefinition
import mihon.desktop.extension.SourcePreferenceOption
import mihon.desktop.extension.SourcePreferenceType
import mihon.desktop.extension.SourcePreferencesSnapshot
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test

class SourcePreferencesScreenTest {

    private val source = SourceDescriptor(
        id = 4242L,
        name = "Sample Source",
        lang = "en",
        className = "ext.test.SampleSource",
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows clear empty state when source has no configurable preferences`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 800.dp)) {
                SourcePreferencesScreen(
                    source = source,
                    definitions = emptyList(),
                    values = emptyMap(),
                    onBack = {},
                    onValueChange = { _, _ -> },
                )
            }
        }

        onNodeWithTag("source-preferences-screen").assertIsDisplayed()
        onNodeWithTag("source-preferences-empty").assertIsDisplayed()
        onNodeWithTag("source-preferences-title").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders supported boolean and text preferences and reports changes`() = runComposeUiTest {
        var changed: Pair<String, String>? = null
        val definitions = listOf(
            SourcePreferenceDefinition(
                key = "dataSaver",
                title = "Data Saver",
                type = SourcePreferenceType.Boolean,
                defaultValue = "false",
            ),
            SourcePreferenceDefinition(
                key = "apiKey",
                title = "API Key",
                type = SourcePreferenceType.String,
                defaultValue = "",
            ),
            SourcePreferenceDefinition(
                key = "pageLimit",
                title = "Page Limit",
                type = SourcePreferenceType.Int,
                defaultValue = "20",
            ),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                SourcePreferencesScreen(
                    source = source,
                    definitions = definitions,
                    values = emptyMap(),
                    onBack = {},
                    onValueChange = { key, value -> changed = key to value },
                )
            }
        }

        onNodeWithTag("source-preference-dataSaver").assertIsDisplayed()
        onNodeWithTag("source-preference-apiKey").performScrollTo().assertIsDisplayed()
        onNodeWithTag("source-preference-pageLimit").performScrollTo().assertIsDisplayed()

        onNodeWithTag("source-preference-switch-dataSaver").performScrollTo().performClick()
        changed shouldBe ("dataSaver" to "true")

        onNodeWithTag("source-preference-field-apiKey").performScrollTo().performTextInput("secret")
        changed shouldBe ("apiKey" to "secret")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `loads real extension preferences and saves supported control changes`() = runComposeUiTest {
        val setCalls = mutableListOf<Triple<Long, String, String>>()
        val snapshot = SourcePreferencesSnapshot(
            supported = true,
            definitions = listOf(
                SourcePreferenceDefinition(
                    key = "dataSaver",
                    title = "Data Saver",
                    type = SourcePreferenceType.Boolean,
                    defaultValue = "false",
                    currentValue = "true",
                ),
                SourcePreferenceDefinition(
                    key = "quality",
                    title = "Quality",
                    type = SourcePreferenceType.Select,
                    defaultValue = "low",
                    currentValue = "low",
                    options = listOf(
                        SourcePreferenceOption("Low", "low"),
                        SourcePreferenceOption("High", "high"),
                    ),
                ),
                SourcePreferenceDefinition(
                    key = "genres",
                    title = "Genres",
                    type = SourcePreferenceType.List,
                    defaultValue = "action",
                    currentValue = "action",
                    options = listOf(
                        SourcePreferenceOption("Action", "action"),
                        SourcePreferenceOption("Comedy", "comedy"),
                    ),
                ),
                SourcePreferenceDefinition(
                    key = "custom",
                    title = "Custom Preference",
                    type = SourcePreferenceType.Unsupported,
                    defaultValue = "raw",
                    currentValue = "raw",
                    isReadOnly = true,
                ),
            ),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                SourcePreferencesScreen(
                    source = source,
                    definitions = emptyList(),
                    values = emptyMap(),
                    onBack = {},
                    onValueChange = { _, _ -> },
                    preferencesProvider = { snapshot },
                    preferenceSetter = { sourceId, key, value ->
                        setCalls += Triple(sourceId, key, value)
                    },
                )
            }
        }

        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("source-preference-dataSaver").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("source-preference-switch-dataSaver").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { setCalls.any { it.second == "dataSaver" } }
        setCalls.last { it.second == "dataSaver" } shouldBe Triple(source.id, "dataSaver", "false")

        onNodeWithTag("source-preference-select-quality").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("source-preference-select-option-quality-high").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("source-preference-select-option-quality-high").performClick()
        waitUntil(timeoutMillis = 15_000) { setCalls.any { it.second == "quality" } }
        setCalls.last { it.second == "quality" } shouldBe Triple(source.id, "quality", "high")

        onNodeWithTag("source-preference-list-checkbox-genres-comedy").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { setCalls.any { it.second == "genres" } }
        setCalls.last { it.second == "genres" } shouldBe Triple(source.id, "genres", "action,comedy")

        onNodeWithTag("source-preference-readonly-custom").performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows unsupported state when source does not implement configurable source`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 800.dp)) {
                SourcePreferencesScreen(
                    source = source,
                    definitions = emptyList(),
                    values = emptyMap(),
                    onBack = {},
                    onValueChange = { _, _ -> },
                    preferencesProvider = { SourcePreferencesSnapshot(supported = false) },
                )
            }
        }

        waitUntil(timeoutMillis = 15_000) {
            onAllNodesWithTag("source-preferences-unsupported").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("source-preferences-unsupported").assertIsDisplayed()
        onNodeWithTag("source-preferences-empty").assertDoesNotExist()
    }
}
