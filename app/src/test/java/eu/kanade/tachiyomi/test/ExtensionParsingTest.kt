package eu.kanade.tachiyomi.test

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File

/**
 * Run this test with: ./gradlew :app:test --tests "eu.kanade.tachiyomi.test.ExtensionParsingTest"
 * Or just run the main function directly
 */
object ExtensionParsingTest {

    private val workDir = File(".")

    private fun readDebugFile(filename: String): String {
        val file = File(workDir, filename)
        return if (file.exists()) file.readText() else ""
    }

    private fun parseDoc(html: String, baseUri: String = ""): Document {
        return Jsoup.parse(html, baseUri)
    }

    fun testNovelsHub() {
        println("\n${"=".repeat(60)}")
        println("TESTING: NovelsHub (RSC/JSON parsing)")
        println("${"=".repeat(60)}")

        val html = readDebugFile("debug_novelshub_new.txt")
        if (html.isEmpty()) {
            println("ERROR: debug_novelshub_new.txt not found")
            return
        }

        println("File size: ${html.length} bytes")

        // Test the RSC chapter regex pattern
        val chapterRegex = Regex(""""id":\d+,"slug":"(chapter-\d+)","number":(\d+)""")
        val matches = chapterRegex.findAll(html)
        val chapters = matches.toList()

        println("Chapters found with regex: ${chapters.size}")
        if (chapters.isNotEmpty()) {
            println("First 3 chapters:")
            chapters.take(3).forEach { match ->
                println("  slug: ${match.groupValues[1]}, number: ${match.groupValues[2]}")
            }
        }
    }

    fun testZetroTranslation() {
        println("\n${"=".repeat(60)}")
        println("TESTING: ZetroTranslation (Madara)")
        println("${"=".repeat(60)}")

        val novelHtml = readDebugFile("debug_zetro.txt")
        if (novelHtml.isEmpty()) {
            println("ERROR: debug_zetro.txt not found")
            return
        }

        val novelDoc = parseDoc(novelHtml, "https://zetrotranslation.com")
        println("Novel page size: ${novelHtml.length} bytes")

        // ID extraction
        val ratingPostId = novelDoc.selectFirst(".rating-post-id")?.attr("value")
        val mangaChaptersHolder = novelDoc.selectFirst("#manga-chapters-holder")?.attr("data-id")
        val shortlink = novelDoc.selectFirst("link[rel=shortlink]")?.attr("href")
        val shortlinkId = shortlink?.let { Regex("""[?&]p=(\d+)""").find(it)?.groupValues?.get(1) }

        println("ID extraction:")
        println("  .rating-post-id value: $ratingPostId")
        println("  #manga-chapters-holder data-id: $mangaChaptersHolder")
        println("  shortlink: $shortlink")
        println("  shortlink ID: $shortlinkId")

        // Test AJAX response
        val ajaxHtml = readDebugFile("debug_zetro_ajax.txt")
        if (ajaxHtml.isNotEmpty()) {
            val ajaxDoc = parseDoc(ajaxHtml, "https://zetrotranslation.com")
            val chapters = ajaxDoc.select(".wp-manga-chapter a")
            println("\nAJAX chapters found: ${chapters.size}")
            if (chapters.isNotEmpty()) {
                println("First chapter: ${chapters.first()?.text()}")
                println("First chapter URL: ${chapters.first()?.attr("href")}")
            }
        }

        // Test chapter content
        val chapterHtml = readDebugFile("debug_zetro_chapter.txt")
        if (chapterHtml.isNotEmpty()) {
            val chapterDoc = parseDoc(chapterHtml, "https://zetrotranslation.com")
            println("\nChapter content selectors:")
            listOf(".text-left", ".reading-content", ".entry-content").forEach { sel ->
                val elem = chapterDoc.selectFirst(sel)
                println("  $sel: ${if (elem != null) "FOUND (${elem.text().length} chars)" else "NOT FOUND"}")
            }
        }
    }

    fun testSRankManga() {
        println("\n${"=".repeat(60)}")
        println("TESTING: SRankManga (Madara)")
        println("${"=".repeat(60)}")

        val html = readDebugFile("debug_srankmanga.txt")
        if (html.isEmpty()) {
            println("ERROR: debug_srankmanga.txt not found")
            return
        }

        val doc = parseDoc(html, "https://srankmanga.com")
        println("Page size: ${html.length} bytes")

        val mangaChaptersHolder = doc.selectFirst("#manga-chapters-holder")?.attr("data-id")
        println("manga-chapters-holder data-id: $mangaChaptersHolder")

        val chaptersInPage = doc.select(".wp-manga-chapter")
        println("Chapters in page HTML: ${chaptersInPage.size}")
    }

    fun testSonicMTL() {
        println("\n${"=".repeat(60)}")
        println("TESTING: SonicMTL (Madara)")
        println("${"=".repeat(60)}")

        val html = readDebugFile("debug_sonicmtl.txt")
        if (html.isEmpty()) {
            println("ERROR: debug_sonicmtl.txt not found")
            return
        }

        val doc = parseDoc(html, "https://sonicmtl.com")
        println("Page size: ${html.length} bytes")

        val mangaChaptersHolder = doc.selectFirst("#manga-chapters-holder")?.attr("data-id")
        println("manga-chapters-holder data-id: $mangaChaptersHolder")

        val chapters = doc.select(".wp-manga-chapter")
        println("Chapters in page: ${chapters.size}")
    }

    fun testRequiemTLS() {
        println("\n${"=".repeat(60)}")
        println("TESTING: RequiemTLS (LightNovelWP)")
        println("${"=".repeat(60)}")

        val html = readDebugFile("debug_requiemtls.txt")
        if (html.isEmpty()) {
            println("ERROR: debug_requiemtls.txt not found")
            return
        }

        val doc = parseDoc(html, "https://requiemtls.com")
        println("Page size: ${html.length} bytes")

        val chapterSelectors = listOf(".eplister li", ".eplister li[data-num]", "div.eplister a")
        println("Chapter selectors:")
        chapterSelectors.forEach { sel ->
            val elems = doc.select(sel)
            println("  $sel: ${elems.size} items")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Working directory: ${workDir.absolutePath}")
        testNovelsHub()
        testZetroTranslation()
        testSRankManga()
        testSonicMTL()
        testRequiemTLS()
        println("\nTEST COMPLETE")
    }
}
