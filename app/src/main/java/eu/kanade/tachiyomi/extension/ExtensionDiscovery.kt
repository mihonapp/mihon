package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source

/**
 * Interface for discovering extensions at test time.
 * This allows for both mock implementations (unit tests) and real implementations (instrumented tests).
 */
interface ExtensionDiscovery {
    /**
     * Get all installed extensions
     */
    fun getInstalledExtensions(): List<ExtensionInfo>

    /**
     * Get all testable sources from installed extensions
     */
    fun getTestableSources(): List<CatalogueSource>

    /**
     * Get sources filtered by type
     */
    fun getNovelSources(): List<CatalogueSource>
    fun getMangaSources(): List<CatalogueSource>
}

/**
 * Lightweight info about an installed extension
 */
data class ExtensionInfo(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val lang: String,
    val isNsfw: Boolean,
    val isNovel: Boolean,
    val sources: List<SourceInfo>,
)

data class SourceInfo(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String?,
    val source: CatalogueSource?,
)

/**
 * Real implementation that uses ExtensionManager to discover installed extensions.
 * Used in instrumented tests where Android context is available.
 */
class RealExtensionDiscovery(
    private val extensionManager: ExtensionManager,
) : ExtensionDiscovery {

    override fun getInstalledExtensions(): List<ExtensionInfo> {
        return extensionManager.installedExtensionsFlow.value.map { ext ->
            ExtensionInfo(
                name = ext.name,
                pkgName = ext.pkgName,
                versionName = ext.versionName,
                lang = ext.lang,
                isNsfw = ext.isNsfw,
                isNovel = ext.isNovel,
                sources = ext.sources.map { source ->
                    SourceInfo(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl,
                        source = source as? CatalogueSource,
                    )
                },
            )
        }
    }

    override fun getTestableSources(): List<CatalogueSource> {
        return extensionManager.installedExtensionsFlow.value
            .flatMap { ext -> ext.sources }
            .filterIsInstance<CatalogueSource>()
    }

    override fun getNovelSources(): List<CatalogueSource> {
        return extensionManager.installedExtensionsFlow.value
            .filter { it.isNovel }
            .flatMap { ext -> ext.sources }
            .filterIsInstance<CatalogueSource>()
    }

    override fun getMangaSources(): List<CatalogueSource> {
        return extensionManager.installedExtensionsFlow.value
            .filter { !it.isNovel }
            .flatMap { ext -> ext.sources }
            .filterIsInstance<CatalogueSource>()
    }
}

/**
 * Mock implementation for unit tests that don't have Android context.
 * Register mock sources for testing.
 */
class MockExtensionDiscovery : ExtensionDiscovery {

    private val mockSources = mutableListOf<MockExtension>()

    fun registerMockExtension(
        name: String,
        sources: List<CatalogueSource>,
        isNovel: Boolean = false,
    ) {
        mockSources.add(MockExtension(name, sources, isNovel))
    }

    override fun getInstalledExtensions(): List<ExtensionInfo> {
        return mockSources.map { mock ->
            ExtensionInfo(
                name = mock.name,
                pkgName = "mock.${mock.name.lowercase().replace(" ", ".")}",
                versionName = "1.0.0",
                lang = "en",
                isNsfw = false,
                isNovel = mock.isNovel,
                sources = mock.sources.map { source ->
                    SourceInfo(
                        id = source.id,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = (source as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl,
                        source = source,
                    )
                },
            )
        }
    }

    override fun getTestableSources(): List<CatalogueSource> {
        return mockSources.flatMap { it.sources }
    }

    override fun getNovelSources(): List<CatalogueSource> {
        return mockSources.filter { it.isNovel }.flatMap { it.sources }
    }

    override fun getMangaSources(): List<CatalogueSource> {
        return mockSources.filter { !it.isNovel }.flatMap { it.sources }
    }

    private data class MockExtension(
        val name: String,
        val sources: List<CatalogueSource>,
        val isNovel: Boolean,
    )
}
