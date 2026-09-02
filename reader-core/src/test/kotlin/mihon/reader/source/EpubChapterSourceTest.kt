package mihon.reader.source

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path

class EpubChapterSourceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `EPUB emits XHTML images in spine and document order`(): Unit = runBlocking {
        val path = temporaryDirectory.resolve("book.zip")
        ArchiveFixtures.writeEpub(path)
        LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "book.zip")).use { source ->
            source.pages().map { it.id.entryName }.shouldContainExactly("OPS/images/10.png", "OPS/images/2.png")
            source.open(source.pages().first().id).use { it.input.readBytes().toString(Charsets.UTF_8) shouldBe "ten" }
        }
    }

    @Test
    fun `EPUB accepts direct image spine items and local parent references`(): Unit = runBlocking {
        val opf = """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest><item id="image" href="images/2.png" media-type="image/png"/></manifest>
              <spine><itemref idref="image"/></spine>
            </package>"""
        val path = temporaryDirectory.resolve("direct.epub")
        ArchiveFixtures.writeEpub(path, opf = opf)
        LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "direct.epub")).use { source ->
            source.pages().map { it.id.entryName }.shouldContainExactly("OPS/images/2.png")
        }
    }

    @Test
    fun `EPUB rejects external data javascript absolute and escaped image references`() {
        listOf(
            "https://example.invalid/page.png",
            "data:image/png;base64,AA==",
            "javascript:alert(1)",
            "file:///C:/secret.png",
            "//127.0.0.1/page.png",
            "../../../escape.png",
            "..%2f..%2fescape.png",
        ).forEachIndexed { index, reference ->
            val xhtml = """<?xml version="1.0"?>
                <html xmlns="http://www.w3.org/1999/xhtml"><body><img src="$reference"/></body></html>"""
            val path = temporaryDirectory.resolve("external-$index.epub")
            ArchiveFixtures.writeEpub(path, xhtml = xhtml)
            shouldThrow<ReaderFailure.UnsafePath> {
                LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, path.fileName.toString()))
            }
        }
    }

    @Test
    fun `container OPF and XHTML reject every external XML mechanism without network requests`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 200
            val networkUrl = "http://127.0.0.1:${server.localPort}/external.dtd"
            val sentinel = temporaryDirectory.resolve("sentinel.txt")
            Files.writeString(sentinel, "EXTERNAL-CONTENT")
            val fileUrl = sentinel.toUri().toString()

            XmlTarget.entries.forEach { target ->
                Attack.entries.forEachIndexed { index, attack ->
                    val documents = maliciousDocuments(target, attack, fileUrl, networkUrl)
                    val path = temporaryDirectory.resolve("${target.name}-${attack.name}-$index.epub")
                    ArchiveFixtures.writeEpub(
                        path,
                        containerXml = documents.container,
                        opf = documents.opf,
                        xhtml = documents.xhtml,
                    )
                    shouldThrow<ReaderFailure.XmlRejected> {
                        LocalChapterSourceFactory().create(
                            ArchiveFixtures.asset(temporaryDirectory, path.fileName.toString()),
                        )
                    }
                }
            }
            shouldThrow<SocketTimeoutException> { server.accept() }
        }
    }

    @Test
    fun `XML size and nesting limits are deterministic`() {
        val oversized = temporaryDirectory.resolve("oversized.epub")
        ArchiveFixtures.writeEpub(
            oversized,
            containerXml = "<container>" + " ".repeat((ReaderLimits.MAX_XML_BYTES + 1).toInt()) + "</container>",
        )
        shouldThrow<ReaderFailure.LimitExceeded> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "oversized.epub"))
        }

        val depth = ReaderLimits.MAX_XML_DEPTH + 1
        val deeplyNested = "<container>" + "<n>".repeat(depth) + "</n>".repeat(depth) + "</container>"
        val deep = temporaryDirectory.resolve("deep.epub")
        ArchiveFixtures.writeEpub(deep, containerXml = deeplyNested)
        shouldThrow<ReaderFailure.XmlRejected> {
            LocalChapterSourceFactory().create(ArchiveFixtures.asset(temporaryDirectory, "deep.epub"))
        }
    }
}

private enum class XmlTarget { CONTAINER, OPF, XHTML }

private enum class Attack { FILE_XXE, NETWORK_XXE, PARAMETER_ENTITY, BILLION_LAUGHS, XINCLUDE, EXTERNAL_SCHEMA }

private data class EpubDocuments(
    val container: String,
    val opf: String,
    val xhtml: String,
)

private fun maliciousDocuments(
    target: XmlTarget,
    attack: Attack,
    fileUrl: String,
    networkUrl: String,
): EpubDocuments {
    val base = when (target) {
        XmlTarget.CONTAINER -> ArchiveFixtures.DEFAULT_CONTAINER
        XmlTarget.OPF -> ArchiveFixtures.DEFAULT_OPF
        XmlTarget.XHTML -> ArchiveFixtures.DEFAULT_XHTML
    }
    val rootName = when (target) {
        XmlTarget.CONTAINER -> "container"
        XmlTarget.OPF -> "package"
        XmlTarget.XHTML -> "html"
    }
    val attacked = when (attack) {
        Attack.FILE_XXE -> insertDoctype(base, rootName, "<!ENTITY xxe SYSTEM \"$fileUrl\">")
        Attack.NETWORK_XXE -> insertDoctype(base, rootName, "<!ENTITY xxe SYSTEM \"$networkUrl\">")
        Attack.PARAMETER_ENTITY -> insertDoctype(
            base,
            rootName,
            "<!ENTITY % external SYSTEM \"$networkUrl\"> %external;",
        )
        Attack.BILLION_LAUGHS -> insertDoctype(
            base,
            rootName,
            "<!ENTITY a \"ha\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">",
        )
        Attack.XINCLUDE -> injectIntoRoot(
            base,
            " xmlns:xi=\"http://www.w3.org/2001/XInclude\"",
            "<xi:include href=\"$networkUrl\" parse=\"text\"/>",
        )
        Attack.EXTERNAL_SCHEMA -> injectIntoRoot(
            base,
            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:test $networkUrl\"",
            "",
        )
    }
    return EpubDocuments(
        container = if (target == XmlTarget.CONTAINER) attacked else ArchiveFixtures.DEFAULT_CONTAINER,
        opf = if (target == XmlTarget.OPF) attacked else ArchiveFixtures.DEFAULT_OPF,
        xhtml = if (target == XmlTarget.XHTML) attacked else ArchiveFixtures.DEFAULT_XHTML,
    )
}

private fun insertDoctype(xml: String, rootName: String, declarations: String): String =
    xml.replaceFirst("?>", "?>\n<!DOCTYPE $rootName [$declarations]>")

private fun injectIntoRoot(xml: String, rootAttributes: String, child: String): String {
    val declarationEnd = xml.indexOf("?>") + 2
    val rootStart = xml.indexOf('<', declarationEnd)
    val rootEnd = xml.indexOf('>', rootStart)
    return buildString {
        append(xml, 0, rootEnd)
        append(rootAttributes)
        append('>')
        append(child)
        append(xml, rootEnd + 1, xml.length)
    }
}
