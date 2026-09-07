package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionStoreItem
import org.junit.jupiter.api.Test

class BrowseScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `switches tabs between sources and extensions`() = runComposeUiTest {
        var selectedTab = BrowseTab.Sources

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseScreen(
                    state = BrowseUiState(
                        selectedTab = selectedTab,
                        availableExtensions = listOf(
                            ExtensionStoreItem(
                                pkg = "ext.test.one",
                                name = "Extension One",
                                version = "1.0.0",
                                versionCode = 1,
                            ),
                        ),
                    ),
                    onTabSelected = { selectedTab = it },
                    onSearchQueryChange = {},
                    onSourceSelected = { _, _ -> },
                    onInstallExtension = {},
                    onUninstallExtension = {},
                    onToggleExtensionEnabled = { _, _ -> },
                    onAddRepository = {},
                    onRemoveRepository = {},
                )
            }
        }

        onNodeWithTag("browse-tab-sources").assertIsDisplayed()
        onNodeWithTag("browse-tab-extensions").assertIsDisplayed()

        onNodeWithTag("browse-tab-extensions").performClick()
        selectedTab shouldBe BrowseTab.Extensions
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows confirmation dialog when clicking install button`() = runComposeUiTest {
        val storeItem = ExtensionStoreItem(
            pkg = "ext.test.sample",
            name = "Sample Manga",
            version = "1.2.0",
            versionCode = 2,
            lang = "en",
            declaredDomains = listOf("api.sample.com"),
        )

        setContent {
            Box(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                BrowseScreen(
                    state = BrowseUiState(
                        selectedTab = BrowseTab.Extensions,
                        availableExtensions = listOf(storeItem),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSourceSelected = { _, _ -> },
                    onInstallExtension = {},
                    onUninstallExtension = {},
                    onToggleExtensionEnabled = { _, _ -> },
                    onAddRepository = {},
                    onRemoveRepository = {},
                )
            }
        }

        onNodeWithTag("install-btn-ext.test.sample").performClick()
        onNodeWithTag("confirm-install-button").assertIsDisplayed()
        onNodeWithText("Declared Network Domains:").assertIsDisplayed()
    }
}
