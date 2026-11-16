package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager

@AssistedInject
class WebViewViewModel(
    @Assisted val sourceId: Long?,
    private val sourceManager: SourceManager,
    private val network: NetworkHelper,
    private val networkToLocalManga: NetworkToLocalManga,
    val adBlocker: WebViewAdBlocker,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long?): WebViewViewModel
    }

    /** Null until the source it belongs to has been resolved. */
    val headers: StateFlow<Map<String, String>?>
        field = MutableStateFlow<Map<String, String>?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val source = sourceId?.let { sourceManager.get(it) as? HttpSource }
            headers.value = try {
                source?.headers?.toWebViewHeaders().orEmpty()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to build headers" }
                emptyMap()
            }
        }
    }

    fun shareWebpage(context: Context, url: String) {
        try {
            context.startActivity(url.toUri().toShareIntent(context, type = "text/plain"))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        context.openInBrowser(url, forceDefaultBrowser = true)
    }

    suspend fun canOpenManga(url: String): Boolean = withContext(Dispatchers.IO) {
        url.toHttpUrlOrNull()?.let { findMangaLookup(it) } != null
    }

    suspend fun resolveManga(url: String): Long? = withContext(Dispatchers.IO) {
        val pageUrl = url.toHttpUrlOrNull() ?: return@withContext null
        val lookup = findMangaLookup(pageUrl) ?: return@withContext null
        try {
            lookup.getManga()?.let { networkToLocalManga(it.toDomainManga(lookup.sourceId)).id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to open manga from WebView" }
            null
        }
    }

    private suspend fun findMangaLookup(url: HttpUrl): MangaLookup? {
        val link = url.toString()
        var searchLookup: MangaLookup? = null
        for (source in sourceManager.getAll().sortedBy { it.id != sourceId }) {
            try {
                if (source is ResolvableSource) {
                    when (source.getUriType(link)) {
                        UriType.Manga -> return MangaLookup(source.id) { source.getManga(link) }
                        UriType.Chapter -> continue
                        UriType.Unknown -> Unit
                    }
                }
                if (searchLookup != null || source !is HttpSource) continue
                val website = source.getHomeUrl().toHttpUrlOrNull() ?: continue
                if (!website.isSameWebsite(url)) continue

                searchLookup = MangaLookup(source.id) { source.searchByUrl(url) }
                if (source.id == sourceId) return searchLookup
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to check manga URL for source ${source.id}" }
            }
        }
        return searchLookup
    }

    private suspend fun HttpSource.searchByUrl(url: HttpUrl): SManga? {
        return getSearchManga(1, url.toString(), getFilterList()).mangas.firstOrNull { manga ->
            val mangaUrl = getMangaUrl(manga).toHttpUrlOrNull() ?: return@firstOrNull false
            mangaUrl.isSameWebsite(url) &&
                mangaUrl.encodedPath.trimEnd('/') == url.encodedPath.trimEnd('/') &&
                mangaUrl.encodedQuery == url.encodedQuery
        }
    }

    private fun HttpUrl.isSameWebsite(other: HttpUrl): Boolean {
        val useDefaultPorts = port == HttpUrl.defaultPort(scheme) && other.port == HttpUrl.defaultPort(other.scheme)
        return host.removePrefix("www.") == other.host.removePrefix("www.") && (port == other.port || useDefaultPorts)
    }

    private class MangaLookup(val sourceId: Long, val getManga: suspend () -> SManga?)

    fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            logcat { "Cleared $cleared cookies for: $url" }
        }
    }

    fun defaultUserAgentProvider() = network.defaultUserAgentProvider()
}
