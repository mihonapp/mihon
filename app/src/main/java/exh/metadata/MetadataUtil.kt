package exh.metadata

/**
 * Metadata utils
 */
fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

private val KB_FACTOR: Long = 1000
private val KIB_FACTOR: Long = 1024
private val MB_FACTOR = 1000 * KB_FACTOR
private val MIB_FACTOR = 1024 * KIB_FACTOR
private val GB_FACTOR = 1000 * MB_FACTOR
private val GIB_FACTOR = 1024 * MIB_FACTOR

fun parseHumanReadableByteCount(arg0: String): Double? {
    val spaceNdx = arg0.indexOf(" ")
    val ret = java.lang.Double.parseDouble(arg0.substring(0, spaceNdx))
    when (arg0.substring(spaceNdx + 1)) {
        "GB" -> return ret * GB_FACTOR
        "GiB" -> return ret * GIB_FACTOR
        "MB" -> return ret * MB_FACTOR
        "MiB" -> return ret * MIB_FACTOR
        "KB" -> return ret * KB_FACTOR
        "KiB" -> return ret * KIB_FACTOR
    }
    return null
}


fun String?.nullIfBlank(): String? = if(isNullOrBlank())
    null
else
    this

fun <T> ignore(expr: () -> T): T? {
    return try { expr() } catch (t: Throwable) { null }
}

fun <K,V> Set<Map.Entry<K,V>>.forEach(action: (K, V) -> Unit) {
    forEach { action(it.key, it.value) }
}