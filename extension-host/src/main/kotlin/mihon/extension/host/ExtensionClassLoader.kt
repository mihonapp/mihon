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

    override fun findClass(name: String): Class<*> {
        if (!System.getProperty("os.name").startsWith("Windows")) return super.findClass(name)
        val resource = findResource(name.replace('.', '/') + ".class") ?: throw ClassNotFoundException(name)
        // Do not leave a second cached JarFile handle behind when an extension is reloaded on Windows.
        val connection = resource.openConnection().apply { useCaches = false }
        val jar = connection as? java.net.JarURLConnection
        val (original, manifest, certificates) = connection.getInputStream().use {
            Triple(it.readBytes(), jar?.manifest, jar?.certificates)
        }
        val adapted = ExtensionBytecodeCompatibility.adapt(original) ?: return super.findClass(name)
        val source = jar?.jarFileURL ?: getURLs().first { resource.toString().startsWith(it.toString()) }
        check(certificates.isNullOrEmpty()) { "Cannot adapt a signed extension class: $name" }
        val packageName = name.substringBeforeLast('.', "")
        if (packageName.isNotEmpty()) {
            val sealed = manifest?.getAttributes(packageName.replace('.', '/') + "/")?.getValue("Sealed")
                ?: manifest?.mainAttributes?.getValue("Sealed")
            val existing = getDefinedPackage(packageName)
            if (existing == null) {
                if (manifest == null) {
                    definePackage(packageName, null, null, null, null, null, null, null)
                } else {
                    definePackage(packageName, manifest, source)
                }
            } else {
                check(if (existing.isSealed) existing.isSealed(source) else !sealed.equals("true", true)) {
                    "Sealing violation: $packageName"
                }
            }
        }
        return defineClass(name, adapted, 0, adapted.size, java.security.CodeSource(source, certificates))
    }

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
