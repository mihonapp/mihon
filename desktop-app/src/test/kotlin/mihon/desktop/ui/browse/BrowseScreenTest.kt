package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.InstalledExtension
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows trust status and trust revoke actions for an installed extension`() = runComposeUiTest {
        val pkg = "ext.test.installed"
        val installed = InstalledExtension(
            pkg = pkg,
            manifest = ExtensionManifest(
                id = pkg,
                name = "Installed Extension",
                version = "1.0.0",
                versionCode = 1,
                libVersion = 1.4,
                lang = "en",
                sources = listOf(
                    SourceDescriptor(id = 1L, name = "Source", lang = "en", className = "ext.TestSource"),
                ),
            ),
            installDir = "/tmp/installed",
            packageFile = "/tmp/installed.mext",
            installedAt = 1L,
        )
        var trusted: InstalledExtension? = null
        var revoked: InstalledExtension? = null

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                BrowseScreen(
                    state = BrowseUiState(
                        selectedTab = BrowseTab.Extensions,
                        installedExtensions = listOf(installed),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSourceSelected = { _, _ -> },
                    onInstallExtension = {},
                    onUninstallExtension = {},
                    onToggleExtensionEnabled = { _, _ -> },
                    onAddRepository = {},
                    onRemoveRepository = {},
                    extensionTrustStatuses = mapOf(pkg to ExtensionTrustStatus.TRUSTED),
                    onTrustExtension = { trusted = it },
                    onRevokeExtension = { revoked = it },
                )
            }
        }

        onNodeWithTag("extension-trust-status-$pkg").assertTextContains("Trusted")
        onNodeWithTag("trust-btn-$pkg").performClick()
        trusted?.pkg shouldBe pkg
        onNodeWithTag("revoke-btn-$pkg").performClick()
        revoked?.pkg shouldBe pkg
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `confirming an unsigned store install marks explicit trust`() = runComposeUiTest {
        val storeItem = ExtensionStoreItem(
            pkg = "ext.test.unsigned",
            name = "Unsigned Extension",
            version = "1.0.0",
            versionCode = 1,
        )
        var installedItem: ExtensionStoreItem? = null

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                BrowseScreen(
                    state = BrowseUiState(
                        selectedTab = BrowseTab.Extensions,
                        availableExtensions = listOf(storeItem),
                    ),
                    onTabSelected = {},
                    onSearchQueryChange = {},
                    onSourceSelected = { _, _ -> },
                    onInstallExtension = { installedItem = it },
                    onUninstallExtension = {},
                    onToggleExtensionEnabled = { _, _ -> },
                    onAddRepository = {},
                    onRemoveRepository = {},
                )
            }
        }

        onNodeWithTag("install-btn-${storeItem.pkg}").performClick()
        onNodeWithTag("confirm-install-button").performClick()
        installedItem?.trustOnInstall shouldBe true
    }
}
