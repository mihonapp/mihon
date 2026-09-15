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

    const val CONVERTER_VERSION = "7"
    const val COMPAT_VERSION = "3"

    @Synchronized
    fun convertToMext(
        sourceFile: File,
        targetMextFile: File,
        storeItem: ExtensionStoreItem? = null,
        converterVersion: String = CONVERTER_VERSION,
        compatibilityVersion: String = COMPAT_VERSION,
    ): ExtensionManifest {
        val digest = MessageDigest.getInstance("SHA-256")
        sourceFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val key = "$hash\n$converterVersion\n$compatibilityVersion\n${json.encodeToString(storeItem)}"
        if (targetMextFile.isFile) {
            val cached = runCatching {
                ZipFile(targetMextFile).use { zip ->
                    val entry = zip.getEntry("conversion.key") ?: return@use null
                    if (zip.getInputStream(entry).bufferedReader().use { it.readText() } != key) return@use null
                    mihon.extension.validator.ExtensionPackageValidator.validatePackage(targetMextFile)
                    json.decodeFromString<ExtensionManifest>(
                        zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use {
                            it.readText()
                        },
                    )
                }
            }.getOrNull()
            if (cached != null) return cached
        }
        val target = targetMextFile.absoluteFile
        target.parentFile.mkdirs()
        val temporary = File.createTempFile("conversion-", ".mext", target.parentFile)
        try {
            val manifest = convertUncached(sourceFile, temporary, storeItem, key)
            try {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            }
            return manifest
        } finally {
            temporary.delete()
        }
    }

    private fun convertUncached(
        sourceFile: File,
        targetMextFile: File,
        storeItem: ExtensionStoreItem?,
        cacheKey: String,
    ): ExtensionManifest {
        var manifestBytes: ByteArray? = null
        val dexEntries = sortedMapOf<String, ByteArray>()
        val assets = linkedMapOf<String, ByteArray>()
        var iconBytes: ByteArray? = null
        val jarEntries = mutableMapOf<String, ByteArray>()
        var hasClasses = false

        ZipFile(sourceFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!entry.isDirectory && name.startsWith("assets/")) {
                    require(!name.contains("..") && !name.contains("\\")) { "Unsafe asset path: $name" }
                    assets[name] = zip.getInputStream(entry).use { it.readBytes() }
                }
                if (name == "AndroidManifest.xml") {
                    manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                } else if (Regex("classes(?:[0-9]+)?\\.dex").matches(name)) {
                    dexEntries[name] = zip.getInputStream(entry).use { it.readBytes() }
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

        require(info.libVersion in 1.3..1.6) {
            "Unsupported extension API ${info.libVersion}; supported range is 1.3 through 1.6"
        }
        val standardApi = Regex("^(\\d+\\.\\d+)(?:\\.|$)").find(info.versionName)?.groupValues?.get(1)?.toDoubleOrNull()
        require(standardApi != null && standardApi in 1.3..1.6) {
            "Unsupported APK API in version '${info.versionName}'; supported range is 1.3 through 1.6"
        }
        require(info.className.isNotBlank()) { "Extension entry class is missing" }

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
            libVersion = standardApi,
            lang = storeItem?.lang ?: "all",
            isNsfw = info.isNsfw || (storeItem?.isNsfw == true),
            sources = sources,
            // Never invent a "*" wildcard for untrusted packages. Store items carry verified
            // declared domains; direct APK/JAR installs have no repository metadata, so the
            // manifest stays explicit and the network helper denies anything not declared.
            declaredDomains = storeItem?.declaredDomains.orEmpty(),
        )

        mihon.extension.validator.ExtensionPackageValidator.validateManifest(manifest)

        // Translate every DEX independently; factories can live in secondary dex files.
        val generatedJars = linkedMapOf<String, ByteArray>()
        for ((dexName, dexBytes) in dexEntries) {
            val tempDex = File.createTempFile("ext_dex_", ".dex")
            val tempJar = File.createTempFile("ext_jar_", ".jar")
            try {
                tempDex.writeBytes(dexBytes)
                Dex2jar.from(DexConstructorNormalizer.reader(dexBytes)).to(tempJar.toPath())
                if (tempJar.exists() && tempJar.length() > 0) {
                    generatedJars[dexName.removeSuffix(".dex") + ".jar"] = tempJar.readBytes()
                }
            } finally {
                tempDex.delete()
                tempJar.delete()
            }
        }

        val hierarchy = mutableMapOf<String, Pair<String?, List<String>>>()
        fun parents(type: String): Pair<String?, List<String>> = hierarchy.getOrPut(type) {
            val stream = javaClass.classLoader.getResourceAsStream("$type.class")
                ?: throw IllegalArgumentException("Unsupported extension dependency while computing JVM frames: $type")
            stream.use {
                org.objectweb.asm.ClassReader(it).let { reader ->
                    reader.superName to
                        reader.interfaces.toList()
                }
            }
        }
        for (jar in generatedJars.values) {
            java.util.zip.ZipInputStream(jar.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name.endsWith(".class")) {
                        val reader = org.objectweb.asm.ClassReader(input.readBytes())
                        hierarchy[reader.className] = reader.superName to reader.interfaces.toList()
                    }
                }
            }
        }
        fun ancestors(type: String): Set<String> {
            if (type.startsWith(
                    "[",
                )
            ) {
                return setOf(type, "java/lang/Object", "java/lang/Cloneable", "java/io/Serializable")
            }
            val result = linkedSetOf<String>()
            fun visit(value: String) {
                if (!result.add(value)) return
                val (base, interfaces) = parents(value)
                base?.let(::visit)
                interfaces.forEach(::visit)
            }
            visit(type)
            return result
        }
        for ((jarName, jar) in generatedJars.toMap()) {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                java.util.zip.ZipInputStream(jar.inputStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val bytes = input.readBytes()
                        zip.putNextEntry(ZipEntry(entry.name))
                        if (entry.name.endsWith(".class")) {
                            rejectInvalidConstructorLowering(bytes, sourceFile.name)
                            val writer = object : org.objectweb.asm.ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
                                override fun getCommonSuperClass(type1: String, type2: String): String {
                                    if (type1 == type2) return type1
                                    if (type1.startsWith("[") || type2.startsWith("[")) {
                                        if (type1 in setOf("java/lang/Cloneable", "java/io/Serializable")) return type1
                                        if (type2 in setOf("java/lang/Cloneable", "java/io/Serializable")) return type2
                                        if (type1.startsWith("[") && type2.startsWith("[")) {
                                            val a = type1.substring(1)
                                            val b = type2.substring(1)
                                            if ((a.startsWith("L") || a.startsWith("[")) &&
                                                (b.startsWith("L") || b.startsWith("["))
                                            ) {
                                                val common = getCommonSuperClass(
                                                    if (a.startsWith("L")) {
                                                        a.substring(
                                                            1,
                                                            a.length - 1,
                                                        )
                                                    } else {
                                                        a
                                                    },
                                                    if (b.startsWith("L")) {
                                                        b.substring(
                                                            1,
                                                            b.length - 1,
                                                        )
                                                    } else {
                                                        b
                                                    },
                                                )
                                                return "[" + if (common.startsWith("[")) common else "L$common;"
                                            }
                                        }
                                        return "java/lang/Object"
                                    }
                                    val first = ancestors(type1)
                                    val second = ancestors(type2)
                                    if (type1 in second) return type1
                                    if (type2 in first) return type2
                                    var current = type1
                                    while (current !in
                                        second
                                    ) {
                                        current = parents(current).first ?: return "java/lang/Object"
                                    }
                                    return current
                                }
                            }
                            org.objectweb.asm.ClassReader(
                                bytes,
                            ).accept(writer, org.objectweb.asm.ClassReader.SKIP_FRAMES)
                            zip.write(writer.toByteArray())
                        } else {
                            zip.write(bytes)
                        }
                        zip.closeEntry()
                    }
                }
            }
            generatedJars[jarName] = output.toByteArray()
        }
        targetMextFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(targetMextFile)).use { zos ->
            zos.putNextEntry(ZipEntry("conversion.key"))
            zos.write(cacheKey.toByteArray())
            zos.closeEntry()
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
            for ((jarName, jarBytes) in generatedJars) {
                zos.putNextEntry(ZipEntry(jarName))
                zos.write(jarBytes)
                zos.closeEntry()
            }
            for ((assetName, assetBytes) in assets) {
                zos.putNextEntry(ZipEntry(assetName))
                zos.write(assetBytes)
                zos.closeEntry()
            }
            if (hasClasses) {
                zos.putNextEntry(ZipEntry(if (generatedJars.isEmpty()) "classes.jar" else "supplied-classes.jar"))
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

        mihon.extension.validator.ExtensionPackageValidator.validatePackage(targetMextFile)
        return manifest
    }

    /** Keep runtime IDs and languages while retaining the APK entry factory for the next load. */
    internal fun rejectInvalidConstructorLowering(bytes: ByteArray, sourceName: String) {
        val node = org.objectweb.asm.tree.ClassNode()
        org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.SKIP_DEBUG)
        for (method in node.methods) {
            val instructions = method.instructions.toArray().filter { it.opcode >= 0 }
            for (window in instructions.windowed(4)) {
                val allocation = window[0] as? org.objectweb.asm.tree.TypeInsnNode ?: continue
                val constructor = window[2] as? org.objectweb.asm.tree.MethodInsnNode ?: continue
                val assignment = window[3] as? org.objectweb.asm.tree.FieldInsnNode ?: continue
                require(
                    !(
                        allocation.opcode == org.objectweb.asm.Opcodes.NEW && allocation.desc == "java/lang/Object" &&
                            window[1].opcode == org.objectweb.asm.Opcodes.DUP && constructor.name == "<init>" &&
                            constructor.owner == "java/lang/Object" &&
                            assignment.opcode == org.objectweb.asm.Opcodes.PUTSTATIC &&
                            assignment.desc != "Ljava/lang/Object;"
                        ),
                ) {
                    "Unsupported DEX constructor lowering in $sourceName: ${node.name}.${method.name}; " +
                        "converter created Object for ${assignment.desc}"
                }
            }
        }
    }

    /** Keep runtime IDs and languages while retaining the APK entry factory for the next load. */
    fun replaceSourceIdentities(
        packageFile: File,
        manifest: ExtensionManifest,
        sources: List<SourceDescriptor>,
    ): ExtensionManifest {
        require(sources.isNotEmpty()) { "Extension did not expose any sources" }
        val entryClass = manifest.sources.first().className
        val resolved = manifest.copy(sources = sources.distinctBy { it.id }.map { it.copy(className = entryClass) })
        mihon.extension.validator.ExtensionPackageValidator.validateManifest(resolved)
        val replacement = File.createTempFile("mext_identity_", ".mext", packageFile.parentFile)
        try {
            ZipFile(packageFile).use { zip ->
                ZipOutputStream(replacement.outputStream()).use { output ->
                    zip.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        if (entry.name == "manifest.json") {
                            output.write(json.encodeToString(resolved).toByteArray())
                        } else if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
            mihon.extension.validator.ExtensionPackageValidator.validatePackage(replacement)
            java.nio.file.Files.move(
                replacement.toPath(),
                packageFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            replacement.delete()
        }
        return resolved
    }

    private fun generateSourceId(name: String, lang: String): Long {
        val key = "${name.lowercase()}/$lang/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return buffer.long and Long.MAX_VALUE
    }
}
