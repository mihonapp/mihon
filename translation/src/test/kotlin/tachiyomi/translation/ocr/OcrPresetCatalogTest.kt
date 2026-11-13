package tachiyomi.translation.ocr

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class OcrPresetCatalogTest {
    @Test
    fun `catalog exposes one dedicated and four multilingual presets`() {
        // Only Korean keeps a dedicated model: Japanese runs Manga OCR and English the generic.
        OcrPresetCatalog.languagePresets shouldHaveSize 1
        OcrPresetCatalog.multilingualPresets shouldHaveSize 4
        OcrPresetCatalog.presets shouldHaveSize 5
    }

    @Test
    fun `every preset has a sha256 and a resolvable url`() {
        OcrPresetCatalog.presets.forEach { preset ->
            preset.sha256.length shouldBe 64
            OcrPresetCatalog.downloadUrl(preset) shouldBe
                (preset.url ?: "${OcrPresetCatalog.BASE_URL}/${preset.fileName}")
        }
    }

    @Test
    fun `direct-file presets point at model files and a checksummed dictionary`() {
        OcrPresetCatalog.languagePresets.forEach { preset ->
            preset.isDirectFile shouldBe true
            preset.fileName.endsWith(".onnx") shouldBe true
            preset.charsetUrl shouldNotBe null
            preset.charsetFileName shouldNotBe null
            preset.charsetSha256?.length shouldBe 64
        }
    }

    @Test
    fun `default is the small v6 model`() {
        OcrPresetCatalog.default.id shouldBe OcrPresetCatalog.ID_SMALL
    }

    @Test
    fun `korean picks the dedicated v5 model first`() {
        OcrPresetCatalog.candidatesFor("ko", null).first().id shouldBe OcrPresetCatalog.ID_KO_V5
    }

    @Test
    fun `english and japanese fall back to the generic model`() {
        // Their dedicated exports are closed off; the engine choice handles the difference.
        OcrPresetCatalog.candidatesFor("en", OcrPresetCatalog.ID_SMALL).first().id shouldBe
            OcrPresetCatalog.ID_SMALL
        OcrPresetCatalog.candidatesFor("ja", OcrPresetCatalog.ID_SMALL).first().id shouldBe
            OcrPresetCatalog.ID_SMALL
        OcrPresetCatalog.languagePreset("en") shouldBe null
        OcrPresetCatalog.languagePreset("ja") shouldBe null
    }

    @Test
    fun `ppocrv6 tiny is never tried for japanese`() {
        // Its dictionary dropped Japanese, so it must not appear even as a fallback.
        OcrPresetCatalog.candidatesFor("ja", OcrPresetCatalog.ID_TINY).map { it.id }
            .contains(OcrPresetCatalog.ID_TINY) shouldBe false
    }

    @Test
    fun `a korean-only preference is ignored for japanese`() {
        OcrPresetCatalog.candidatesFor("ja", OcrPresetCatalog.ID_KO_V5).map { it.id }
            .contains(OcrPresetCatalog.ID_KO_V5) shouldBe false
    }

    @Test
    fun `v5 mobile is not offered for japanese or korean`() {
        OcrPresetCatalog.forLanguage("ja").map { it.id }
            .contains(OcrPresetCatalog.ID_V5_MOBILE) shouldBe false
        OcrPresetCatalog.forLanguage("ko").map { it.id }
            .contains(OcrPresetCatalog.ID_V5_MOBILE) shouldBe false
    }

    @Test
    fun `v6 presets cover all three source languages`() {
        listOf("ja", "ko", "en").forEach { language ->
            OcrPresetCatalog.forLanguage(language).map { it.id }
                .contains(OcrPresetCatalog.ID_SMALL) shouldBe true
        }
    }

    @Test
    fun `unknown id resolves to null`() {
        OcrPresetCatalog.byId("nope") shouldBe null
        OcrPresetCatalog.byId(null) shouldBe null
        OcrPresetCatalog.byId(OcrPresetCatalog.ID_TINY) shouldNotBe null
    }
}
