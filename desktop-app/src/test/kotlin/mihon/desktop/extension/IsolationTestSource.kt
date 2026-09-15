package mihon.desktop.extension

import eu.kanade.tachiyomi.network.asObservableSuccess
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.Page
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import java.io.File

open class IsolationTestSource(override val id: Long) : WindowsCatalogueSource {
    override val name = "Isolation $id"
    override val lang = "en"
    override suspend fun getPopularManga(page: Int): MangasPage {
        File("own.txt").writeText(id.toString())
        return MangasPage(listOf(SManga(url = "/$id", title = id.toString())), false)
    }
    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val result = runCatching { File(query).writeText("escaped") }.fold({ "escaped" }, { "denied" })
        return MangasPage(listOf(SManga(url = "/result", title = result)), false)
    }
    override suspend fun getMangaDetails(manga: SManga) = manga
    override suspend fun getChapterList(manga: SManga) = emptyList<SChapter>()
    override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
}
class IsolationSourceOne : IsolationTestSource(901)
class IsolationSourceTwo : IsolationTestSource(902)

class RejectedNetworkSource : IsolationTestSource(904), mihon.extension.source.WindowsHttpSource {
    override val baseUrl = "https://blocked.example"

    @Suppress("DEPRECATION")
    override suspend fun getPopularManga(page: Int): MangasPage {
        eu.kanade.tachiyomi.network.NetworkHelper().client.newCall(
            okhttp3.Request.Builder().url(baseUrl).build(),
        ).asObservableSuccess().toBlocking().first().close()
        return MangasPage(emptyList(), false)
    }
}

class DetachedNetworkSource : IsolationTestSource(903), mihon.extension.source.WindowsHttpSource {
    override val baseUrl = "https://detached.example"
    override suspend fun getPopularManga(page: Int): MangasPage {
        // Zstd's JNI loader creates NIO temporary files before loading its native library.
        java.nio.file.Files.delete(java.nio.file.Files.createTempFile("extension-", ".tmp"))
        check(File.createTempFile("extension-", ".tmp").delete())
        check(File.createTempFile("extension-", null, File(".")).delete())
        val result = java.util.concurrent.CompletableFuture.supplyAsync {
            eu.kanade.tachiyomi.network.NetworkHelper().client.newCall(
                okhttp3.Request.Builder().url(baseUrl).build(),
            ).execute().use { it.body.string() }
        }.get()
        return MangasPage(listOf(SManga(url = "/result", title = result)), false)
    }
    override suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val identity = mihon.extension.host.ExtensionExecutionContext.Identity(
            "test.other",
            902,
            android.app.Application(),
        )
        val result = mihon.extension.host.ExtensionExecutionContext.duringConstruction(identity) {
            eu.kanade.tachiyomi.network.NetworkHelper().client.newCall(
                okhttp3.Request.Builder().url(baseUrl).build(),
            ).execute().use { it.body.string() }
        }
        return MangasPage(listOf(SManga(url = "/result", title = result)), false)
    }
}
