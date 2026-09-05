package mihon.extension.host

import mihon.extension.source.WindowsSource
import java.net.URL
import java.net.URLClassLoader

class ExtensionClassLoader(
    urls: Array<URL>,
    parent: ClassLoader = ExtensionClassLoader::class.java.classLoader,
) : URLClassLoader(urls, parent) {

    fun instantiateSource(className: String, httpClient: BrokeredHttpClient? = null): WindowsSource {
        val clazz = loadClass(className)
        val constructorWithClient = clazz.constructors.firstOrNull { c ->
            c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(BrokeredHttpClient::class.java)
        }

        val instance = if (constructorWithClient != null && httpClient != null) {
            constructorWithClient.newInstance(httpClient)
        } else {
            clazz.getDeclaredConstructor().newInstance()
        }

        return instance as? WindowsSource
            ?: throw IllegalArgumentException("Class '$className' does not implement WindowsSource")
    }
}
