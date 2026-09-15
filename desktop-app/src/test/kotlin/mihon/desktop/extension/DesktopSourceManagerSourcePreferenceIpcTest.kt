package mihon.desktop.extension

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceOptionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopSourceManagerSourcePreferenceIpcTest {

    @Test
    fun `loading for business restores stored preferences before settings are opened`(
        @TempDir tempDir: Path,
    ): Unit = runBlocking {
        val sourceId = 7299L
        val store = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
        val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), store)
        installExtension(installer, tempDir, sourceId, emptyList())
        val proc = FakePreferenceProcessManager(tempDir.resolve("work").toFile(), remotePreferences(sourceId))
        val manager = DesktopSourceManager(installer, proc, store)
        try {
            manager.setSourcePreferenceValue(sourceId, "apiKey", "persisted-key")
            coroutineScope { List(8) { async { manager.ensureSourceLoaded(sourceId) } }.forEach { it.await() } }
            proc.setCalls shouldContainExactly
                listOf(Triple(sourceId, "apiKey", StringPreferenceValueDto("persisted-key")))
            manager.ensureSourceLoaded(sourceId)
            proc.setCalls.size shouldBe 1
            manager.unloadExtension("ext.preferences.$sourceId")
            manager.ensureSourceLoaded(sourceId)
            proc.setCalls.size shouldBe 2
        } finally {
            manager.close()
        }
    }

    private class FakePreferenceProcessManager(
        workingDirectory: File,
        var response: SourcePreferencesDto,
    ) : WindowsExtensionProcessManager(workingDirectory) {

        val setCalls = mutableListOf<Triple<Long, String, SourcePreferenceValueDto>>()

        private fun loadedSource() = SourceDescriptor(
            id = response.sourceId,
            name = "Preference Source ${response.sourceId}",
            lang = "en",
            className = "ext.PreferenceSource${response.sourceId}",
        )

        override suspend fun loadExtension(packageFile: File): List<SourceDescriptor> = listOf(loadedSource())

        override suspend fun getSources(): List<SourceDescriptor> = listOf(loadedSource())

        override suspend fun getSourcePreferencesResult(sourceId: Long): SourcePreferencesDto = response

        override suspend fun setSourcePreference(
            sourceId: Long,
            key: String,
            value: SourcePreferenceValueDto,
        ): SourcePreferenceValueDto {
            setCalls += Triple(sourceId, key, value)
            return value
        }
    }

    private fun remotePreferences(sourceId: Long): SourcePreferencesDto = SourcePreferencesDto(
        sourceId = sourceId,
        supported = true,
        definitions = listOf(
            SourcePreferenceDefinitionDto(
                key = "dataSaver",
                title = "Data Saver",
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
                key = "quality",
                title = "Quality",
                type = SourcePreferenceTypeDto.SELECT,
                defaultValue = StringPreferenceValueDto("low"),
                currentValue = StringPreferenceValueDto("high"),
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
        ),
    )

    @Test
    fun `manager maps extension host preferences and persists set values`(@TempDir tempDir: Path) {
        runBlocking {
            val sourceId = 7200L
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            installExtension(installer, tempDir, sourceId, capabilities = emptyList())

            val processManager = FakePreferenceProcessManager(
                workingDirectory = tempDir.resolve("work").toFile(),
                response = remotePreferences(sourceId),
            )
            val manager = DesktopSourceManager(
                installer = installer,
                processManager = processManager,
                preferenceStore = prefStore,
            )
            try {
                val definitions = manager.getSourcePreferences(sourceId)
                definitions.map { it.key } shouldContainExactly listOf(
                    "dataSaver",
                    "apiKey",
                    "quality",
                    "genres",
                    "custom",
                )

                definitions.first { it.key == "dataSaver" }.currentValue shouldBe "true"
                definitions.first { it.key == "dataSaver" }.type shouldBe SourcePreferenceType.Boolean
                definitions.first { it.key == "quality" }.type shouldBe SourcePreferenceType.Select
                definitions.first { it.key == "quality" }.options.map { it.value } shouldContainExactly
                    listOf("low", "high")
                definitions.first { it.key == "genres" }.type shouldBe SourcePreferenceType.List
                definitions.first { it.key == "genres" }.currentValue shouldBe "action,comedy"
                definitions.first { it.key == "custom" }.isReadOnly shouldBe true
                definitions.first { it.key == "custom" }.type shouldBe SourcePreferenceType.Unsupported

                manager.setSourcePreference(sourceId, "apiKey", "new-secret")
                processManager.setCalls.single() shouldBe Triple(
                    sourceId,
                    "apiKey",
                    StringPreferenceValueDto("new-secret"),
                )
                manager.getSourcePreferenceValue(sourceId, "apiKey") shouldBe "new-secret"
                prefStore.property("extension.source.preference.$sourceId.apiKey") shouldBe "new-secret"

                val reopened = DesktopSourceManager(
                    installer = installer,
                    processManager = null,
                    preferenceStore = prefStore,
                )
                try {
                    reopened.getSourcePreferenceValue(sourceId, "apiKey") shouldBe "new-secret"
                } finally {
                    reopened.close()
                }
            } finally {
                manager.close()
            }
        }
    }

    @Test
    fun `manager falls back to manifest definitions when ipc reports unsupported`(@TempDir tempDir: Path) {
        runBlocking {
            val sourceId = 7201L
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            installExtension(
                installer = installer,
                tempDir = tempDir,
                sourceId = sourceId,
                capabilities = listOf(
                    "source_preferences",
                    "pref:string:apiKey:API Key",
                ),
            )

            val processManager = FakePreferenceProcessManager(
                workingDirectory = tempDir.resolve("work-fallback").toFile(),
                response = SourcePreferencesDto(sourceId = sourceId, supported = false),
            )
            val manager = DesktopSourceManager(
                installer = installer,
                processManager = processManager,
                preferenceStore = prefStore,
            )
            try {
                val snapshot = manager.getSourcePreferencesSnapshot(sourceId)
                snapshot.supported shouldBe true
                snapshot.definitions.map { it.key } shouldContainExactly listOf("apiKey")
                snapshot.definitions.single().type shouldBe SourcePreferenceType.String

                manager.setSourcePreference(sourceId, "apiKey", "fallback-value")
                processManager.setCalls shouldBe emptyList()
                manager.getSourcePreferenceValue(sourceId, "apiKey") shouldBe "fallback-value"
            } finally {
                manager.close()
            }
        }
    }

    @Test
    fun `manager reports unsupported when neither ipc nor manifest exposes preferences`(@TempDir tempDir: Path) {
        runBlocking {
            val sourceId = 7202L
            val prefStore = DesktopPreferenceStore(tempDir.resolve("prefs.properties"))
            val installer = DesktopExtensionInstaller(tempDir.resolve("extensions").toFile(), prefStore)
            installExtension(installer, tempDir, sourceId, capabilities = emptyList())

            val processManager = FakePreferenceProcessManager(
                workingDirectory = tempDir.resolve("work-unsupported").toFile(),
                response = SourcePreferencesDto(sourceId = sourceId, supported = false),
            )
            val manager = DesktopSourceManager(
                installer = installer,
                processManager = processManager,
                preferenceStore = prefStore,
            )
            try {
                val snapshot = manager.getSourcePreferencesSnapshot(sourceId)
                snapshot.supported shouldBe false
                snapshot.definitions shouldBe emptyList()
                manager.isSourceConfigurable(sourceId) shouldBe false
            } finally {
                manager.close()
            }
        }
    }

    private suspend fun installExtension(
        installer: DesktopExtensionInstaller,
        tempDir: Path,
        sourceId: Long,
        capabilities: List<String>,
    ) {
        val manifest = ExtensionManifest(
            id = "ext.preferences.$sourceId",
            name = "Preference Extension $sourceId",
            version = "1.0.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            sources = listOf(
                SourceDescriptor(
                    id = sourceId,
                    name = "Preference Source $sourceId",
                    lang = "en",
                    className = "ext.PreferenceSource$sourceId",
                ),
            ),
            capabilities = capabilities,
        )
        val mextFile = tempDir.resolve("preferences-$sourceId.mext").toFile()
        ZipOutputStream(FileOutputStream(mextFile)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(Json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
        installer.installFromLocalFile(mextFile, trustOnInstall = true)
    }
}
