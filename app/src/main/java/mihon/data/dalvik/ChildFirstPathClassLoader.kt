package mihon.data.dalvik

import android.os.Build
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.Collections
import java.util.Enumeration

/**
 * Returns the class loader used to run an extension's dex.
 *
 * Extensions are compiled on their own and treat everything the host provides — Kotlin runtime,
 * okhttp, jsoup, rx, the source API — as `compileOnly`, so their bytecode references those classes
 * by their original names. The lookup order is therefore what decides which copy of a class an
 * extension ends up with:
 *
 * 1. boot / system classes
 * 2. the extension's own dex, so it can ship its own multisrc implementation
 * 3. the host, so `eu.kanade.tachiyomi.source.*` and friends resolve back to the single copy the
 *    app holds and the ABI stays unified
 *
 * ART recognizes the built-in loader and maps the app images compiled for this app's class loader
 * chain, and before Android 17 a damaged one crashes every launch until the app is updated. ART
 * doesn't recognize the backport, so the platform loader is used where it's safe to do so.
 */
fun extensionClassLoader(
    dexPath: String,
    parent: ClassLoader,
): ClassLoader {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        DelegateLastClassLoader(dexPath, null, parent)
    } else {
        ChildFirstPathClassLoader(dexPath, parent)
    }
}

/**
 * A [PathClassLoader] whose lookup order is system → own dex → [parent], i.e. child first, the
 * order extensions are built against.
 */
internal class ChildFirstPathClassLoader(
    dexPath: String,
    parent: ClassLoader,
) : PathClassLoader(dexPath, null, parent) {

    private val systemClassLoader: ClassLoader? = getSystemClassLoader()

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        val loaded = findLoadedClass(name)
            ?: systemClassLoader?.runCatching { loadClass(name) }?.getOrNull()
            ?: runCatching { findClass(name) }.getOrNull()
            ?: super.loadClass(name, resolve)
        if (resolve) resolveClass(loaded)
        return loaded
    }

    override fun getResource(name: String?): URL? {
        return systemClassLoader?.getResource(name)
            ?: findResource(name)
            ?: super.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            listOf(systemClassLoader?.getResources(name), findResources(name), parent?.getResources(name))
                .forEach { found ->
                    while (found?.hasMoreElements() == true) add(found.nextElement())
                }
        }
        return Collections.enumeration(resources)
    }

    override fun getResourceAsStream(name: String?): InputStream? {
        return try {
            getResource(name)?.openStream()
        } catch (_: IOException) {
            null
        }
    }
}
