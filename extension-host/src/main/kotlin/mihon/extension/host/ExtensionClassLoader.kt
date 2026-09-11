package mihon.extension.host

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceFactory
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.source.WindowsSource
import java.net.URL
import java.net.URLClassLoader

class ExtensionClassLoader(
    urls: Array<URL>,
    parent: ClassLoader = ExtensionClassLoader::class.java.classLoader,
) : URLClassLoader(urls, parent) {

    fun instantiateSources(className: String, httpClient: BrokeredHttpClient? = null): List<WindowsSource> {
        val clazz = loadClass(className)

        // Case 1: Standard WindowsSource (.mext native)
        if (WindowsSource::class.java.isAssignableFrom(clazz)) {
            val constructorWithClient = clazz.constructors.firstOrNull { c ->
                c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(BrokeredHttpClient::class.java)
            }
            val instance = if (constructorWithClient != null && httpClient != null) {
                constructorWithClient.newInstance(httpClient)
            } else {
                clazz.getDeclaredConstructor().newInstance()
            }
            return listOf(instance as WindowsSource)
        }

        // Case 2: Tachiyomi SourceFactory
        if (SourceFactory::class.java.isAssignableFrom(clazz)) {
            val factory = clazz.getDeclaredConstructor().newInstance() as SourceFactory
            return factory.createSources()
                .filterIsInstance<CatalogueSource>()
                .map { TachiyomiCatalogueSourceAdapter(it) }
        }

        // Case 3: Tachiyomi CatalogueSource (or HttpSource / ParsedHttpSource)
        if (CatalogueSource::class.java.isAssignableFrom(clazz)) {
            val instance = clazz.getDeclaredConstructor().newInstance() as CatalogueSource
            return listOf(TachiyomiCatalogueSourceAdapter(instance))
        }

        throw IllegalArgumentException(
            "Class '$className' does not implement WindowsSource, CatalogueSource, or SourceFactory",
        )
    }

    fun instantiateSource(className: String, httpClient: BrokeredHttpClient? = null): WindowsSource {
        val list = instantiateSources(className, httpClient)
        return list.firstOrNull()
            ?: throw IllegalArgumentException("No sources created for class '$className'")
    }
}
