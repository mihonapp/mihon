package mihon.desktop.extension.compat

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class TachiyomiManifestInfo(
    val packageId: String,
    val versionName: String,
    val versionCode: Long,
    val name: String,
    val className: String,
    val isNsfw: Boolean = false,
    val libVersion: Double = 1.4,
)

object AxmlManifestParser {

    fun parse(manifestBytes: ByteArray): TachiyomiManifestInfo {
        if (manifestBytes.size >= 4 &&
            manifestBytes[0] == 0x03.toByte() &&
            manifestBytes[1] == 0x00.toByte() &&
            manifestBytes[2] == 0x08.toByte() &&
            manifestBytes[3] == 0x00.toByte()
        ) {
            return parseBinaryAxml(manifestBytes)
        }
        return parsePlainXml(manifestBytes.decodeToString())
    }

    fun parsePlainXml(xml: String): TachiyomiManifestInfo {
        val packageRegex = Regex("""package\s*=\s*["']([^"']+)["']""")
        val versionCodeRegex = Regex("""android:versionCode\s*=\s*["']?([0-9]+)["']?""")
        val versionNameRegex = Regex("""android:versionName\s*=\s*["']([^"']+)["']""")
        val classRegex =
            Regex(
                """<meta-data[^>]*android:name\s*=\s*["']tachiyomi\.extension\.(?:class|factory\.class)["'][^>]*android:value\s*=\s*["']([^"']+)["']""",
            )
        val altClassRegex =
            Regex(
                """<meta-data[^>]*android:value\s*=\s*["']([^"']+)["'][^>]*android:name\s*=\s*["']tachiyomi\.extension\.(?:class|factory\.class)["']""",
            )
        val nameRegex =
            Regex(
                """<meta-data[^>]*android:name\s*=\s*["']tachiyomix\.name["'][^>]*android:value\s*=\s*["']([^"']+)["']""",
            )
        val labelRegex = Regex("""android:label\s*=\s*["'](?:Tachiyomi:\s*)?([^"']+)["']""")
        val nsfwRegex =
            Regex(
                """<meta-data[^>]*android:name\s*=\s*["']tachiyomi\.extension\.nsfw["'][^>]*android:value\s*=\s*["']1["']""",
            )
        val libVersionRegex =
            Regex(
                """<meta-data[^>]*android:name\s*=\s*["']tachiyomix\.extensionLib["'][^>]*android:value\s*=\s*["']([^"']+)["']""",
            )

        val packageId = packageRegex.find(xml)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Could not find package attribute in AndroidManifest.xml")
        val versionCode = versionCodeRegex.find(xml)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
        val versionName = versionNameRegex.find(xml)?.groupValues?.get(1) ?: "1.0.0"
        val rawClassName = classRegex.find(xml)?.groupValues?.get(1)
            ?: altClassRegex.find(xml)?.groupValues?.get(1)
            ?: ""
        val className = if (rawClassName.startsWith(".")) packageId + rawClassName else rawClassName
        val name = nameRegex.find(xml)?.groupValues?.get(1)
            ?: labelRegex.find(xml)?.groupValues?.get(1)
            ?: packageId.substringAfterLast('.')
        val isNsfw = nsfwRegex.containsMatchIn(xml)
        val libVersion = libVersionRegex.find(xml)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.4

        return TachiyomiManifestInfo(
            packageId = packageId,
            versionName = versionName,
            versionCode = versionCode,
            name = name,
            className = className,
            isNsfw = isNsfw,
            libVersion = libVersion,
        )
    }

    private fun parseBinaryAxml(bytes: ByteArray): TachiyomiManifestInfo {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.getInt(0)
        require(magic == 0x00080003) { "Invalid AXML magic: $magic" }

        var pos = 8
        val strings = mutableListOf<String>()
        var packageId = ""
        var versionName = "1.0.0"
        var versionCode = 1L
        var name = ""
        var className = ""
        var isNsfw = false
        var libVersion = 1.4

        while (pos + 8 <= bytes.size) {
            val chunkType = buffer.getShort(pos).toInt() and 0xFFFF
            val headerSize = buffer.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buffer.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > bytes.size) break

            when (chunkType) {
                0x0001 -> { // String pool chunk
                    val stringCount = buffer.getInt(pos + 8)
                    val flags = buffer.getInt(pos + 16)
                    val isUtf8 = (flags and (1 shl 8)) != 0
                    val stringsStart = buffer.getInt(pos + 20)
                    val poolBase = pos + stringsStart

                    val offsets = IntArray(stringCount)
                    for (i in 0 until stringCount) {
                        offsets[i] = buffer.getInt(pos + 28 + i * 4)
                    }

                    for (off in offsets) {
                        var p = poolBase + off
                        if (p >= bytes.size) {
                            strings.add("")
                            continue
                        }
                        if (isUtf8) {
                            val l1 = bytes[p].toInt() and 0xFF
                            p += if (l1 < 0x80) 1 else 2
                            val l2 = bytes[p].toInt() and 0xFF
                            p += if (l2 < 0x80) 1 else 2
                            val end = (p + l2).coerceAtMost(bytes.size)
                            strings.add(bytes.copyOfRange(p, end).decodeToString())
                        } else {
                            val l = buffer.getShort(p).toInt() and 0xFFFF
                            p += if ((l and 0x8000) != 0) 4 else 2
                            val charCount = l and 0x7FFF
                            val byteCount = charCount * 2
                            val end = (p + byteCount).coerceAtMost(bytes.size)
                            strings.add(String(bytes, p, end - p, Charsets.UTF_16LE))
                        }
                    }
                }

                0x0102 -> { // Start tag chunk
                    val tagNsIdx = buffer.getInt(pos + 16)
                    val tagNameIdx = buffer.getInt(pos + 20)
                    val attrStart = buffer.getShort(pos + 28).toInt() and 0xFFFF
                    val attrCount = buffer.getShort(pos + 32).toInt() and 0xFFFF
                    val tagName = strings.getOrNull(tagNameIdx) ?: ""

                    val attrMap = mutableMapOf<String, String>()
                    var attrPos = pos + 20 + attrStart
                    for (i in 0 until attrCount) {
                        if (attrPos + 20 > bytes.size) break
                        val aNameIdx = buffer.getInt(attrPos + 4)
                        val aValIdx = buffer.getInt(attrPos + 8)
                        val aDataType = (buffer.get(attrPos + 15).toInt() and 0xFF)
                        val aData = buffer.getInt(attrPos + 16)

                        val aName = strings.getOrNull(aNameIdx) ?: ""
                        val aVal = if (aValIdx != -1 && aValIdx < strings.size) {
                            strings[aValIdx]
                        } else if (aDataType == 0x10 || aDataType == 0x11) { // decimal or hex integer
                            aData.toString()
                        } else if (aDataType == 0x12) { // boolean
                            if (aData != 0) "true" else "false"
                        } else {
                            aData.toString()
                        }
                        attrMap[aName] = aVal
                        attrPos += 20
                    }

                    when (tagName) {
                        "manifest" -> {
                            attrMap["package"]?.let { packageId = it }
                            attrMap["versionName"]?.let { versionName = it }
                            attrMap["versionCode"]?.toLongOrNull()?.let { versionCode = it }
                        }
                        "application" -> {
                            attrMap["label"]?.let {
                                if (name.isBlank()) {
                                    name = it.removePrefix("Tachiyomi: ").trim()
                                }
                            }
                        }
                        "meta-data" -> {
                            val mName = attrMap["name"]
                            val mVal = attrMap["value"]
                            if (mName != null && mVal != null) {
                                when (mName) {
                                    "tachiyomi.extension.class", "tachiyomi.extension.factory.class" -> {
                                        className = mVal
                                    }
                                    "tachiyomix.name" -> {
                                        name = mVal
                                    }
                                    "tachiyomi.extension.nsfw" -> {
                                        isNsfw = mVal == "1" || mVal.equals("true", ignoreCase = true)
                                    }
                                    "tachiyomix.contentWarning" -> {
                                        if (mVal == "2" || mVal == "3") isNsfw = true
                                    }
                                    "tachiyomix.extensionLib" -> {
                                        mVal.toDoubleOrNull()?.let { libVersion = it }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            pos += chunkSize
        }

        if (packageId.isBlank()) {
            // Fallback: look for package-like string in strings pool
            packageId = strings.firstOrNull { it.startsWith("eu.kanade.tachiyomi.extension.") } ?: "unknown.extension"
        }
        if (name.isBlank()) {
            name = packageId.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        val resolvedClassName = if (className.startsWith(".")) packageId + className else className

        return TachiyomiManifestInfo(
            packageId = packageId,
            versionName = versionName,
            versionCode = versionCode,
            name = name,
            className = resolvedClassName,
            isNsfw = isNsfw,
            libVersion = libVersion,
        )
    }
}
