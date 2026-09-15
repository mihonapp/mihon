package mihon.extension.host

import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.ChapterPayload
import mihon.extension.ipc.GetPagePayload
import mihon.extension.ipc.ImageFilePayload
import mihon.extension.ipc.ImagePayload
import mihon.extension.ipc.IpcCommands
import mihon.extension.ipc.IpcRequest
import mihon.extension.ipc.IpcResponse
import mihon.extension.ipc.LoadExtensionPayload
import mihon.extension.ipc.MangaPayload
import mihon.extension.ipc.SearchPayload
import mihon.extension.ipc.SetSourcePreferencePayload
import mihon.extension.ipc.SourcePayload
import mihon.extension.ipc.SourcePreferencesDto
import mihon.extension.ipc.applyStateFrom
import mihon.extension.ipc.decodeFilterList
import mihon.extension.ipc.encodeFilterList
import mihon.extension.ipc.encodeSourcePreferenceValue
import mihon.extension.ipc.encodeSourcePreferences
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource
import mihon.extension.source.WindowsImageSource
import mihon.extension.source.WindowsSource
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SChapter
import mihon.extension.source.model.SManga
import mihon.extension.validator.ExtensionPackageValidator
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class ExtensionHostEngine(
    val httpClient: BrokeredHttpClient,
) {
    private val sourceRegistryLock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val loadedSources = ConcurrentHashMap<Long, WindowsSource>()
    private val extensionSources = ConcurrentHashMap<String, Set<Long>>()
    private val extensionLoaders = ConcurrentHashMap<String, ExtensionClassLoader>()
    private val sourcePreferenceModels = ConcurrentHashMap<Long, SourcePreferenceModel>()

    private val activeSourceJobs = mutableMapOf<String, MutableSet<kotlinx.coroutines.Job>>()
    private val sourceContexts = ConcurrentHashMap<Long, ExtensionExecutionContext.Identity>()
    private val preferenceContext: android.content.Context
        get() = ExtensionExecutionContext.currentApplication()
    init {
        eu.kanade.tachiyomi.network.NetworkHelper.installBroker(httpClient)
        mihon.extension.host.WebViewBridge.install(httpClient::webView)
        bootstrapInjekt()
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun bootstrapInjekt() {
        Injekt.addFactory<android.content.Context> { ExtensionExecutionContext.currentApplication() }
        Injekt.addFactory<android.app.Application> { ExtensionExecutionContext.currentApplication() }
        registerInjektSingleton { eu.kanade.tachiyomi.network.NetworkHelper() }

        registerInjektSingleton {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            }
        }
        registerInjektSingleton<kotlinx.serialization.protobuf.ProtoBuf> {
            kotlinx.serialization.protobuf.ProtoBuf
        }
    }

    private inline fun <reified T : Any> registerInjektSingleton(factory: () -> T) {
        try {
            Injekt.get<T>()
        } catch (_: Exception) {
            try {
                Injekt.addSingleton(factory())
            } catch (_: Exception) {
                // A concurrent host initialization may have registered the same dependency.
            }
        }
    }

    fun registerSource(source: WindowsSource) {
        synchronized(sourceRegistryLock) {
            loadedSources[source.id] = source
            sourcePreferenceModels.remove(source.id)
        }
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
                if (!entry.isDirectory &&
                    (entry.name.endsWith(".jar") || entry.name.endsWith(".class") || entry.name.startsWith("assets/"))
                ) {
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
        try {
            // Suwayomi loads an extension's main class once and lets SourceFactory provide the
            // authoritative source instances. A repository manifest can contain one descriptor per
            // language even though every descriptor points to that same factory class.
            val application = android.app.Application(
                File(workingDir, "android-data"),
                manifest.id,
                File(workingDir, "assets"),
            )
            val identity = ExtensionExecutionContext.Identity(manifest.id, null, application)
            val instantiatedSources = ExtensionExecutionContext.duringConstruction(identity) {
                manifest.sources
                    .distinctBy { it.className }
                    .flatMap { descriptor -> loader.instantiateSources(descriptor.className, httpClient) }
                    .distinctBy { it.id }
            }

            val newSources = linkedMapOf<Long, WindowsSource>()
            instantiatedSources.forEach { source -> newSources[source.id] = source }
            val loadedDescriptors = newSources.map { (registeredId, source) ->
                SourceDescriptor(
                    registeredId,
                    source.name,
                    source.lang,
                    source::class.java.name,
                    source.supportsLatest,
                    sourceBaseUrl(source),
                )
            }

            synchronized(sourceRegistryLock) {
                activeSourceJobs.remove(manifest.id)?.forEach { it.cancel() }
                val previousSourceIds = extensionSources.remove(manifest.id).orEmpty()
                previousSourceIds.forEach {
                    loadedSources.remove(it)
                    sourcePreferenceModels.remove(it)
                    sourceContexts.remove(it)
                }
                loadedSources.putAll(newSources)
                newSources.keys.forEach { sourceContexts[it] = identity.copy(sourceId = it) }
                extensionSources[manifest.id] = newSources.keys
                extensionLoaders.put(manifest.id, loader)?.let { previous ->
                    runCatching {
                        previous.close()
                    }.onFailure { System.err.println("Old extension loader could not close: ${it.message}") }
                }
            }
            return if (loadedDescriptors.isNotEmpty()) loadedDescriptors else manifest.sources
        } catch (failure: Throwable) {
            runCatching { loader.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun getCatalogueSource(sourceId: Long): WindowsCatalogueSource {
        val source = synchronized(sourceRegistryLock) { loadedSources[sourceId] }
            ?: throw IllegalArgumentException("Source with ID $sourceId not found")
        return source as? WindowsCatalogueSource
            ?: throw IllegalArgumentException("Source $sourceId does not implement WindowsCatalogueSource")
    }

    private fun sourceBaseUrl(source: WindowsSource): String? = runCatching {
        when (source) {
            is mihon.extension.source.WindowsHttpSource -> source.baseUrl
            is TachiyomiCatalogueSourceAdapter -> (source.delegate as? HttpSource)?.baseUrl
            else -> null
        }
    }.getOrNull()

    fun unloadExtension(pkg: String) {
        synchronized(sourceRegistryLock) {
            activeSourceJobs.remove(pkg)?.forEach { it.cancel() }
            extensionSources.remove(pkg).orEmpty().forEach {
                loadedSources.remove(it)
                sourcePreferenceModels.remove(it)
                sourceContexts.remove(it)
            }
            extensionLoaders.remove(pkg)?.close()
        }
    }

    suspend fun handleRequest(request: IpcRequest): IpcResponse {
        val sourceId = runCatching {
            (json.parseToJsonElement(request.payloadJson) as? kotlinx.serialization.json.JsonObject)
                ?.get("sourceId")?.toString()?.toLongOrNull()
        }.getOrNull()
        val identity = sourceId?.let { sourceContexts[it] }
        if (identity == null) return handleBoundRequest(request)
        return coroutineScope {
            val job = currentCoroutineContext().job
            val pkg = requireNotNull(identity.packageId)
            synchronized(sourceRegistryLock) {
                check(sourceContexts[sourceId] === identity) { "Source unloaded before request started" }
                activeSourceJobs.getOrPut(pkg) { mutableSetOf() }.add(job)
            }
            try {
                ExtensionExecutionContext.withIdentity(identity.copy(priority = request.priority)) {
                    handleBoundRequest(request)
                }
            } finally {
                synchronized(sourceRegistryLock) {
                    activeSourceJobs[pkg]?.let { jobs ->
                        jobs.remove(job)
                        if (jobs.isEmpty()) activeSourceJobs.remove(pkg)
                    }
                }
            }
        }
    }

    private suspend fun handleBoundRequest(request: IpcRequest): IpcResponse {
        return try {
            when (request.command) {
                IpcCommands.PING -> IpcResponse(request.requestId, success = true, payloadJson = "pong")

                IpcCommands.LOAD_EXTENSION -> {
                    val payload = json.decodeFromString<LoadExtensionPayload>(request.payloadJson)
                    val sources = loadExtension(File(payload.packagePath), File(payload.workingDir))
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(sources))
                }

                IpcCommands.UNLOAD_EXTENSION -> {
                    val payload = json.decodeFromString<mihon.extension.ipc.UnloadExtensionPayload>(request.payloadJson)
                    unloadExtension(payload.pkg)
                    IpcResponse(request.requestId, success = true)
                }

                IpcCommands.GET_SOURCES -> {
                    val descriptors = synchronized(sourceRegistryLock) {
                        loadedSources.map { (registeredId, source) ->
                            SourceDescriptor(
                                registeredId,
                                source.name,
                                source.lang,
                                source::class.java.name,
                                source.supportsLatest,
                                sourceBaseUrl(source),
                            )
                        }
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
                    val requestedFilters = decodeFilterList(payload.filtersJson)
                    val mangasPage = if (catalogue is TachiyomiCatalogueSourceAdapter) {
                        catalogue.searchMangaWithFilters(payload.page, payload.query, requestedFilters)
                    } else {
                        // Empty payloads keep the legacy "no filters" behavior. Non-empty payloads
                        // are applied to the source's own filter instances so custom subclasses
                        // (and any behavior they carry) survive the round trip. If the source
                        // exposes no filters, use the decoded payload as-is.
                        val sourceFilters = catalogue.getFilterList()
                        val filters = when {
                            requestedFilters.isEmpty() -> FilterList()
                            sourceFilters.isEmpty() -> requestedFilters
                            else -> sourceFilters.applyStateFrom(requestedFilters)
                        }
                        catalogue.searchManga(payload.page, payload.query, filters)
                    }
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

                IpcCommands.GET_IMAGE -> {
                    val payload = json.decodeFromString<ImagePayload>(request.payloadJson)
                    val source = getCatalogueSource(payload.sourceId)
                    val image = if (source is WindowsImageSource) {
                        val bytes = source.getImage(payload.page)
                        val directory = File("page-images").apply { mkdirs() }
                        val file = java.nio.file.Files.createTempFile(directory.toPath(), "page-", ".img").toFile()
                        try {
                            file.writeBytes(bytes)
                        } catch (error: Exception) {
                            file.delete()
                            throw error
                        }
                        ImageFilePayload(file.name)
                    } else {
                        ImageFilePayload()
                    }
                    IpcResponse(request.requestId, success = true, payloadJson = json.encodeToString(image))
                }

                IpcCommands.GET_FILTER_LIST -> {
                    val payload = json.decodeFromString<SourcePayload>(request.payloadJson)
                    val catalogue = getCatalogueSource(payload.sourceId)
                    val filterList = if (catalogue is TachiyomiCatalogueSourceAdapter) {
                        catalogue.getIpcFilterList()
                    } else {
                        catalogue.getFilterList()
                    }
                    IpcResponse(request.requestId, success = true, payloadJson = encodeFilterList(filterList))
                }

                IpcCommands.GET_SOURCE_PREFERENCES -> {
                    val payload = json.decodeFromString<SourcePayload>(request.payloadJson)
                    val source = loadedSources[payload.sourceId]
                        ?: throw IllegalArgumentException("Source with ID ${payload.sourceId} not found")
                    val configurable = source.configurableSourceOrNull()
                    val response = if (configurable == null) {
                        SourcePreferencesDto(sourceId = payload.sourceId, supported = false)
                    } else {
                        val model = sourcePreferenceModels.computeIfAbsent(payload.sourceId) {
                            SourcePreferenceModel(configurable, preferenceContext)
                        }
                        SourcePreferencesDto(
                            sourceId = payload.sourceId,
                            supported = true,
                            definitions = model.definitions(),
                        )
                    }
                    IpcResponse(
                        request.requestId,
                        success = true,
                        payloadJson = encodeSourcePreferences(response),
                    )
                }

                IpcCommands.SET_SOURCE_PREFERENCE -> {
                    val payload = json.decodeFromString<SetSourcePreferencePayload>(request.payloadJson)
                    val source = loadedSources[payload.sourceId]
                        ?: throw IllegalArgumentException("Source with ID ${payload.sourceId} not found")
                    val configurable = source.configurableSourceOrNull()
                        ?: throw IllegalArgumentException(
                            "Source with ID ${payload.sourceId} does not implement ConfigurableSource",
                        )
                    val model = sourcePreferenceModels.computeIfAbsent(payload.sourceId) {
                        SourcePreferenceModel(configurable, preferenceContext)
                    }
                    val updatedValue = model.set(payload.key, payload.value)
                    IpcResponse(
                        request.requestId,
                        success = true,
                        payloadJson = encodeSourcePreferenceValue(updatedValue),
                    )
                }

                else -> IpcResponse(request.requestId, success = false, error = "Unknown command '${request.command}'")
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is VirtualMachineError || e is ThreadDeath) throw e
            System.err.println("Extension request failed: ${request.command}")
            e.printStackTrace(System.err)
            IpcResponse(request.requestId, success = false, error = e.message ?: e::class.java.simpleName)
        }
    }
}
