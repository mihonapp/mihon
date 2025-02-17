package eu.kanade.tachiyomi.data.download.model

import java.util.Locale

class PageData(
    val prefix: String = "",
    val number: String,
    val subNumber: String? = null,
    val type: String,
    val data: ByteArray,
) {
    val filename: String = getFileName(prefix, number, subNumber, type)

    fun split(newData: Array<ByteArray>?): List<PageData> {
        if (newData == null || newData.size < 2) return listOf(this)

        val digitCount = newData.size.toString().length.coerceAtLeast(3)
        val npd = ArrayList<PageData>()
        newData.forEachIndexed { idx, dat ->
            PageData(prefix, number, formatSubPageNumber(idx, digitCount), type, dat)
        }
        return npd
    }

    companion object {
        fun getFileName(prefix: String, number: String, subNumber: String?, type: String): String {
            val sb = StringBuilder()

            if (prefix.isNotEmpty()) {
                sb.append(prefix)
            }

            sb.append(number)

            if (subNumber != null) {
                sb.append(subNumber)
            }

            sb.append('.')
            sb.append(type)

            return sb.toString()
        }

        fun formatSubPageNumber(n: Int, l: Int): String {
            return String.format(
                Locale.ENGLISH,
                "__%0${l}d",
                n + 1,
            )
        }
    }
}
