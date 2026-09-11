package mihon.desktop.extension.compat

import com.googlecode.d2j.dex.Dex2jar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.extension.ExtensionStoreItem
import mihon.extension.model.ExtensionManifest
import mihon.extension.model.SourceDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object TachiyomiExtensionConverter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun isTachiyomiPackage(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                val hasManifest = zip.getEntry("manifest.json") != null
                val hasAndroidManifest = zip.getEntry("AndroidManifest.xml") != null
                val hasDex = zip.getEntry("classes.dex") != null
                !hasManifest && (hasAndroidManifest || hasDex)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun convertToMext(
        sourceFile: File,
        targetMextFile: File,
        storeItem: ExtensionStoreItem? = null,
    ): ExtensionManifest {
        var manifestBytes: ByteArray? = null
        var dexBytes: ByteArray? = null
        var iconBytes: ByteArray? = null
        val jarEntries = mutableMapOf<String, ByteArray>()
        var hasClasses = false

        ZipFile(sourceFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name == "AndroidManifest.xml") {
                    manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                } else if (name == "classes.dex") {
                    dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                } else if (name.endsWith(".class")) {
                    hasClasses = true
                    jarEntries[name] = zip.getInputStream(entry).use { it.readBytes() }
                } else if (iconBytes == null && (
                        name == "icon.png" || name == "icon.jpg" ||
                            name.contains("mipmap-xxxhdpi/ic_launcher") ||
                            name.contains("mipmap-xxhdpi/ic_launcher") ||
                            name.contains("mipmap-xhdpi/ic_launcher") ||
                            name.contains("mipmap-hdpi/ic_launcher") ||
                            name.contains("mipmap-mdpi/ic_launcher") ||
                            name.contains("drawable/ic_launcher") ||
                            name == "assets/icon.png"
                        )
                ) {
                    iconBytes = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }

        requireNotNull(manifestBytes) { "Package does not contain AndroidManifest.xml" }
        val info = AxmlManifestParser.parse(manifestBytes)

        // Determine sources
        val sources = if (storeItem != null && storeItem.sources.isNotEmpty()) {
            storeItem.sources.map { src ->
                if (src.className.isBlank()) {
                    src.copy(className = info.className)
                } else {
                    src
                }
            }
        } else {
            val sourceId = generateSourceId(info.name, storeItem?.lang ?: "all")
            listOf(
                SourceDescriptor(
                    id = sourceId,
                    name = info.name,
                    lang = storeItem?.lang ?: "all",
                    className = info.className,
                    supportsLatest = true,
                ),
            )
        }

        val manifest = ExtensionManifest(
            id = info.packageId,
            name = if (storeItem != null && storeItem.name.isNotBlank()) storeItem.name else info.name,
            version = info.versionName,
            versionCode = info.versionCode,
            libVersion = info.libVersion,
            lang = storeItem?.lang ?: "all",
            isNsfw = info.isNsfw || (storeItem?.isNsfw == true),
            sources = sources,
            // Never invent a "*" wildcard for untrusted packages. Store items carry verified
            // declared domains; direct APK/JAR installs have no repository metadata, so the
            // manifest stays explicit and the network helper denies anything not declared.
            declaredDomains = storeItem?.declaredDomains.orEmpty(),
        )

        // Translate DEX to classes.jar if classes.dex is present
        var generatedJarBytes: ByteArray? = null
        if (dexBytes != null && !hasClasses) {
            val tempDex = File.createTempFile("ext_dex_", ".dex")
            val tempJar = File.createTempFile("ext_jar_", ".jar")
            try {
                tempDex.writeBytes(dexBytes)
                Dex2jar.from(tempDex).to(tempJar.toPath())
                if (tempJar.exists() && tempJar.length() > 0) {
                    generatedJarBytes = tempJar.readBytes()
                }
            } finally {
                tempDex.delete()
                tempJar.delete()
            }
        }

        targetMextFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(targetMextFile)).use { zos ->
            // 1. Write manifest.json
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray())
            zos.closeEntry()

            // 2. Write icon.png if found
            if (iconBytes != null) {
                zos.putNextEntry(ZipEntry("icon.png"))
                zos.write(iconBytes)
                zos.closeEntry()
            }

            // 3. Write compiled classes
            if (generatedJarBytes != null) {
                zos.putNextEntry(ZipEntry("classes.jar"))
                zos.write(generatedJarBytes)
                zos.closeEntry()
            } else if (hasClasses) {
                zos.putNextEntry(ZipEntry("classes.jar"))
                val baos = ByteArrayOutputStream()
                ZipOutputStream(baos).use { jarZos ->
                    for ((cName, cBytes) in jarEntries) {
                        jarZos.putNextEntry(ZipEntry(cName))
                        jarZos.write(cBytes)
                        jarZos.closeEntry()
                    }
                }
                zos.write(baos.toByteArray())
                zos.closeEntry()
            }
        }

        return manifest
    }

    private fun generateSourceId(name: String, lang: String): Long {
        val key = "${name.lowercase()}/$lang/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return buffer.long and Long.MAX_VALUE
    }
}
