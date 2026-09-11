package mihon.desktop.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import mihon.desktop.extension.ExtensionStoreItem
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.extension.SourceState
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test

class ExtensionDetailsScreenTest {

    private val pkg = "ext.test.sample"
    private val source = SourceDescriptor(
        id = 4242L,
        name = "Sample Source",
        lang = "en",
        className = "ext.test.SampleSource",
    )
    private val installed = InstalledExtension(
        pkg = pkg,
        manifest = ExtensionManifest(
            id = pkg,
            name = "Sample Extension",
            version = "1.0.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            isNsfw = true,
            sources = listOf(source),
        ),
        installDir = "/tmp/sample",
        packageFile = "/tmp/sample.mext",
        installedAt = 1_700_000_000_000L,
        repoUrl = "https://example.com/repo",
        isEnabled = true,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders metadata and triggers all extension actions`() = runComposeUiTest {
        var toggled: Boolean? = null
        var updated: ExtensionStoreItem? = null
        var uninstalled = false
        var clearedCookies = false
        var incognito: Boolean? = null
        var openedPreferencesFor: Long? = null
        val updateItem = ExtensionStoreItem(
            pkg = pkg,
            name = "Sample Extension",
            version = "2.0.0",
            versionCode = 2,
        )

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                ExtensionDetailsScreen(
                    extension = installed,
                    sources = listOf(
                        SourceState(
                            source = source,
                            isEnabled = true,
                            isIncognito = false,
                            isConfigurable = true,
                            extensionPackage = pkg,
                        ),
                    ),
                    isExtensionIncognito = false,
                    updateItem = updateItem,
                    onBack = {},
                    onToggleEnabled = { toggled = it },
                    onUpdate = { updated = it },
                    onUninstall = { uninstalled = true },
                    onClearCookies = { clearedCookies = true },
                    onToggleIncognito = { incognito = it },
                    onToggleSourceEnabled = { _, _ -> },
                    onToggleSourceIncognito = { _, _ -> },
                    onOpenSourcePreferences = { openedPreferencesFor = it },
                )
            }
        }

        onNodeWithTag("extension-details-screen").assertIsDisplayed()
        onNodeWithTag("extension-details-name").assertExists()
        onNodeWithTag("extension-details-package").assertExists()
        onNodeWithTag("extension-details-version").assertExists()
        onNodeWithTag("extension-details-language").assertExists()
        onNodeWithTag("extension-details-nsfw").assertExists()
        onNodeWithTag("extension-details-enabled").assertExists()
        onNodeWithTag("extension-details-install-date").assertExists()
        onNodeWithTag("extension-details-repo").assertExists()

        onNodeWithTag("extension-details-toggle-enabled").performScrollTo().performClick()
        toggled shouldBe false

        onNodeWithTag("extension-details-update").performScrollTo().performClick()
        updated?.version shouldBe "2.0.0"

        onNodeWithTag("extension-details-clear-cookies").performScrollTo().performClick()
        clearedCookies shouldBe true

        onNodeWithTag("extension-details-uninstall").performScrollTo().performClick()
        uninstalled shouldBe true

        onNodeWithTag("extension-details-source-preferences").performScrollTo().performClick()
        openedPreferencesFor shouldBe source.id

        onNodeWithTag("extension-details-incognito").performScrollTo().performClick()
        incognito shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `toggles per-source enabled and incognito flags and opens source settings`() = runComposeUiTest {
        var enabledChange: Pair<Long, Boolean>? = null
        var incognitoChange: Pair<Long, Boolean>? = null
        var openedPreferencesFor: Long? = null

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 700.dp)) {
                ExtensionDetailsScreen(
                    extension = installed,
                    sources = listOf(
                        SourceState(
                            source = source,
                            isEnabled = true,
                            isIncognito = false,
                            isConfigurable = true,
                            extensionPackage = pkg,
                        ),
                    ),
                    isExtensionIncognito = false,
                    updateItem = null,
                    onBack = {},
                    onToggleEnabled = {},
                    onUpdate = {},
                    onUninstall = {},
                    onClearCookies = {},
                    onToggleIncognito = {},
                    onToggleSourceEnabled = { id, enabled -> enabledChange = id to enabled },
                    onToggleSourceIncognito = { id, enabled -> incognitoChange = id to enabled },
                    onOpenSourcePreferences = { openedPreferencesFor = it },
                )
            }
        }

        onNodeWithTag("extension-source-enabled-${source.id}").performScrollTo().performClick()
        enabledChange shouldBe (source.id to false)

        onNodeWithTag("extension-source-incognito-${source.id}").performScrollTo().performClick()
        incognitoChange shouldBe (source.id to true)

        onNodeWithTag("extension-source-preferences-${source.id}").performScrollTo().performClick()
        openedPreferencesFor shouldBe source.id
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `shows trust status and triggers trust and revoke actions`() = runComposeUiTest {
        var trustClicked = false
        var revokeClicked = false

        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 900.dp)) {
                ExtensionDetailsScreen(
                    extension = installed,
                    sources = emptyList(),
                    isExtensionIncognito = false,
                    updateItem = null,
                    onBack = {},
                    onToggleEnabled = {},
                    onUpdate = {},
                    onUninstall = {},
                    onClearCookies = {},
                    onToggleIncognito = {},
                    onToggleSourceEnabled = { _, _ -> },
                    onToggleSourceIncognito = { _, _ -> },
                    onOpenSourcePreferences = {},
                    trustStatus = ExtensionTrustStatus.UNTRUSTED,
                    onTrust = { trustClicked = true },
                    onRevoke = { revokeClicked = true },
                )
            }
        }

        onNodeWithTag("extension-details-trust-status").assertTextContains("Untrusted")
        onNodeWithTag("extension-details-trust").performScrollTo().performClick()
        trustClicked shouldBe true
        onNodeWithTag("extension-details-revoke").performScrollTo().performClick()
        revokeClicked shouldBe true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `renders trusted and invalid trust states`() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.requiredSize(1000.dp, 900.dp)) {
                ExtensionDetailsScreen(
                    extension = installed,
                    sources = emptyList(),
                    isExtensionIncognito = false,
                    updateItem = null,
                    onBack = {},
                    onToggleEnabled = {},
                    onUpdate = {},
                    onUninstall = {},
                    onClearCookies = {},
                    onToggleIncognito = {},
                    onToggleSourceEnabled = { _, _ -> },
                    onToggleSourceIncognito = { _, _ -> },
                    onOpenSourcePreferences = {},
                    trustStatus = ExtensionTrustStatus.TRUSTED,
                )
            }
        }

        onNodeWithTag("extension-details-trust-status").assertTextContains("Trusted")
        onNodeWithTag("extension-details-trust-label").assertTextContains("Trust: Trusted")
    }
}
