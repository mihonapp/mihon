package exh.metadata

import java.text.SimpleDateFormat
import java.util.*

/**
 * Metadata utils
 */
fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

private const val KB_FACTOR: Long = 1000
private const val KIB_FACTOR: Long = 1024
private const val MB_FACTOR = 1000 * KB_FACTOR
private const val MIB_FACTOR = 1024 * KIB_FACTOR
private const val GB_FACTOR = 1000 * MB_FACTOR
private const val GIB_FACTOR = 1024 * MIB_FACTOR

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

fun <K,V> Set<Map.Entry<K,V>>.forEach(action: (K, V) -> Unit) {
    forEach { action(it.key, it.value) }
}

val ONGOING_SUFFIX = arrayOf(
        "[ongoing]",
        "(ongoing)",
        "{ongoing}",
        "<ongoing>",
        "ongoing",
        "[incomplete]",
        "(incomplete)",
        "{incomplete}",
        "<incomplete>",
        "incomplete",
        "[wip]",
        "(wip)",
        "{wip}",
        "<wip>",
        "wip"
)

val EX_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
