package mihon.extension.validator

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.Filter
import mihon.extension.source.model.FilterList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionPackageValidatorTest {

    private val json = Json { prettyPrint = true }

    private fun sampleManifest(): ExtensionManifest {
        return ExtensionManifest(
            id = "mihon.extension.en.mangadex",
            name = "MangaDex",
            version = "1.4.0",
            versionCode = 14,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            sources = listOf(
                SourceDescriptor(
                    id = 12345L,
                    name = "MangaDex",
                    lang = "en",
                    className = "mihon.extension.mangadex.MangaDexSource",
                ),
            ),
            declaredDomains = listOf("api.mangadex.org", "*.mangadex.network"),
        )
    }

    private fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun `validateManifest accepts valid manifest`() {
        val manifest = sampleManifest()
        ExtensionPackageValidator.validateManifest(manifest)
    }

    @Test
    fun `validateManifest rejects invalid package id`() {
        val invalidId = sampleManifest().copy(id = "invalid-id-with-dash")
        val ex = assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validateManifest(invalidId)
        }
        ex.message shouldContain "contains invalid characters"
    }

    @Test
    fun `validateManifest rejects blank name`() {
        val invalid = sampleManifest().copy(name = "   ")
        val ex = assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validateManifest(invalid)
        }
        ex.message shouldContain "name must not be blank"
    }

    @Test
    fun `validateManifest rejects non-positive source id`() {
        val invalid = sampleManifest().copy(
            sources = listOf(
                SourceDescriptor(
                    id = 0L,
                    name = "Zero ID",
                    lang = "en",
                    className = "SomeClass",
                ),
            ),
        )
        val ex = assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validateManifest(invalid)
        }
        ex.message shouldContain "Source ID must be positive"
    }

    @Test
    fun `validateManifest rejects invalid declared domain`() {
        val withProtocol = sampleManifest().copy(
            declaredDomains = listOf("https://api.example.com"),
        )
        assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validateManifest(withProtocol)
        }.message shouldContain "bare hostname"

        val withPath = sampleManifest().copy(
            declaredDomains = listOf("api.example.com/api"),
        )
        assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validateManifest(withPath)
        }.message shouldContain "bare hostname"
    }

    @Test
    fun `validatePackageStream accepts valid package with manifest`() {
        val manifest = sampleManifest()
        val manifestBytes = json.encodeToString(manifest).encodeToByteArray()
        val zipBytes = createZip(
            mapOf(
                "manifest.json" to manifestBytes,
                "classes.dex" to "fake dex bytes".encodeToByteArray(),
                "icon.png" to "fake icon bytes".encodeToByteArray(),
            ),
        )

        val result = ExtensionPackageValidator.validatePackageStream(ByteArrayInputStream(zipBytes))
        result.id shouldBe "mihon.extension.en.mangadex"
        result.sources.size shouldBe 1
        result.sources[0].name shouldBe "MangaDex"
    }

    @Test
    fun `validatePackageStream rejects missing manifest`() {
        val zipBytes = createZip(
            mapOf(
                "classes.dex" to "fake dex bytes".encodeToByteArray(),
            ),
        )
        val ex = assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validatePackageStream(ByteArrayInputStream(zipBytes))
        }
        ex.message shouldContain "does not contain manifest.json"
    }

    @Test
    fun `validatePackageStream rejects zip slip path`() {
        val manifestBytes = json.encodeToString(sampleManifest()).encodeToByteArray()
        val zipBytes = createZip(
            mapOf(
                "manifest.json" to manifestBytes,
                "../evil.exe" to "bad code".encodeToByteArray(),
            ),
        )
        val ex = assertThrows<ExtensionValidationException> {
            ExtensionPackageValidator.validatePackageStream(ByteArrayInputStream(zipBytes))
        }
        ex.message shouldContain "Zip slip or unsafe entry rejected"
    }

    @Test
    fun `filter list allows iteration and state mutations`() {
        val textFilter = Filter.Text("Author", "")
        val checkFilter = Filter.CheckBox("Completed", false)
        val selectFilter = Filter.Select("Sort", arrayOf("Popularity", "Date"), 0)
        val filterList = FilterList(textFilter, checkFilter, selectFilter)

        filterList.isEmpty() shouldBe false
        textFilter.state = "Oda"
        checkFilter.state = true
        selectFilter.state = 1

        textFilter.state shouldBe "Oda"
        checkFilter.state shouldBe true
        selectFilter.state shouldBe 1
    }
}
