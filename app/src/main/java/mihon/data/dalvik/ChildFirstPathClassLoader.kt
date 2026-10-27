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

    // Platform classes (java.*, android.*) come from the boot class loader. Deliberately not
    // getSystemClassLoader(), which on Android points at java.class.path and so depends on how the
    // runtime was started rather than on what the platform provides.
    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let {
            if (resolve) resolveClass(it)
            return it
        }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // Not a platform class, so look in the extension's own dex next
            }
        }

        try {
            val fromDex = findClass(name)
            if (resolve) resolveClass(fromDex)
            return fromDex
        } catch (_: ClassNotFoundException) {
            // Not one of its own either, so it has to be a class the host provides
        }

        return super.loadClass(name, resolve)
    }

    override fun getResource(name: String?): URL? {
        return bootClassLoader?.getResource(name)
            ?: findResource(name)
            ?: super.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            listOf(bootClassLoader?.getResources(name), findResources(name), parent?.getResources(name))
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
