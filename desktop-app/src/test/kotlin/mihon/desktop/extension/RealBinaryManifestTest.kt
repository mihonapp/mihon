package mihon.desktop.extension

import mihon.desktop.extension.compat.AxmlManifestParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealBinaryManifestTest {
    @Test
    fun `real compiled APK manifests preserve package version and entry class`() {
        val versions = mapOf(
            "all.mangadex" to "1.6.0",
            "all.mangafire" to "1.6.34",
            "all.mangaplus" to "1.6.66",
            "all.nhentaixxx" to "1.6.11",
            "en.ezmanga" to "1.4.62",
            "en.readcomiconline" to "1.4.44",
            "zh.bilimanga" to "1.6.14",
        )
        for ((suffix, version) in versions) {
            val pkg = "eu.kanade.tachiyomi.extension.$suffix"
            val bytes = javaClass.getResourceAsStream("/real-manifests/$pkg.xml")!!.use { it.readBytes() }
            val info = AxmlManifestParser.parse(bytes)
            assertEquals(pkg, info.packageId)
            assertEquals(version, info.versionName)
            assertTrue(info.className.isNotBlank(), info.className)
            assertTrue(info.versionCode > 0)
        }
    }
}
