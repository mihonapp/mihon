package mihon.extension.host

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.IpcResponse
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import mihon.extension.validator.ExtensionPackageValidator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class ExtensionHostEngine(
    val httpClient: BrokeredHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var activeManifest: ExtensionManifest? = null
    private var classLoader: ExtensionClassLoader? = null
    private val loadedSources = ConcurrentHashMap<Long, WindowsSource>()

    fun registerSource(source: WindowsSource) {
        loadedSources[source.id] = source
    }

    fun loadExtension(packageFile: File, workingDir: File): List<SourceDescriptor> {
        val manifest = ExtensionPackageValidator.validatePackage(packageFile)
        workingDir.mkdirs()

        // Unpack classes / jars from .mext
        val extractedJars = mutableListOf<File>()
        ZipFile(packageFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && (entry.name.endsWith(".jar") || entry.name.endsWith(".class"))) {
                    val dest = File(workingDir, entry.name)
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (entry.name.endsWith(".jar")) {
                        extractedJars.add(dest)
                    }
                }
            }
        }

        val urls = (extractedJars.map { it.toURI().toURL() } + listOf(workingDir.toURI().toURL())).toTypedArray()
        val loader = ExtensionClassLoader(urls)
        this.classLoader = loader
        this.activeManifest = manifest
        this.loadedSources.clear()

        manifest.sources.forEach { descriptor ->
            val sourceInstance = loader.instantiateSource(descriptor.className, httpClient)
            loadedSources[sourceInstance.id] = sourceInstance
        }

        return manifest.sources
    }

    private fun getCatalogueSource(sourceId: Long): WindowsCatalogueSource {
        val source = loadedSources[sourceId] ?: throw IllegalArgumentException("Source with ID $sourceId not found")
        return source as? WindowsCatalogueSource
            ?: throw IllegalArgumentException("Source $sourceId does not implement WindowsCatalogueSource")
    }

    suspend fun handleRequest(request: IpcRequest): IpcResponse {
        return try {
            when (request.command) {
                IpcCommands.PING -> IpcResponse(request.requestId, success = true, payloadJson = "pong")

                IpcCommands.LOAD_EXTENSION -> {
                    val payload = json.decodeFromString<LoadExtensionPayload>(request.payloadJson)
                    val sources = loadExtension(File(payload.packagePath), File(payload.workingDir))
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(sources))
                }

                IpcCommands.GET_SOURCES -> {
                    val descriptors = loadedSources.values.map {
                        SourceDescriptor(it.id, it.name, it.lang, it::class.java.name, it.supportsLatest)
                    }
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(descriptors))
                }

                IpcCommands.GET_POPULAR -> {
                    val payload = json.decodeFromString<GetPagePayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val mangasPage = catalogue.getPopularManga(payload.page)
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
                }

                IpcCommands.GET_LATEST -> {
                    val payload = json.decodeFromString<GetPagePayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val mangasPage = catalogue.getLatestUpdates(payload.page)
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
                }

                IpcCommands.SEARCH_MANGA -> {
                    val payload = json.decodeFromString<SearchPayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val mangasPage = catalogue.searchManga(payload.page, payload.query, FilterList())
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(mangasPage))
                }

                IpcCommands.GET_MANGA_DETAILS -> {
                    val payload = json.decodeFromString<MangaPayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val manga = json.decodeFromString<SManga>(payload.mangaJson)
                    val details = catalogue.getMangaDetails(manga)
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(details))
                }

                IpcCommands.GET_CHAPTER_LIST -> {
                    val payload = json.decodeFromString<MangaPayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val manga = json.decodeFromString<SManga>(payload.mangaJson)
                    val chapters = catalogue.getChapterList(manga)
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(chapters))
                }

                IpcCommands.GET_PAGE_LIST -> {
                    val payload = json.decodeFromString<ChapterPayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val chapter = json.decodeFromString<SChapter>(payload.chapterJson)
                    val pages = catalogue.getPageList(chapter)
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(pages))
                }

                IpcCommands.GET_FILTER_LIST -> {
                    val payload = json.decodeFromString<SourcePayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val filterList = catalogue.getFilterList()
                    val names = filterList.filters.map { it.name }
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(names))
                }

                else -> IpcResponse(request.requestId, success = false, error = "Unknown command '${request.command}'")
            }
        } catch (e: Exception) {
            IpcResponse(request.requestId, success = false, error = e.message ?: e::class.java.simpleName)
        }
    }
}
