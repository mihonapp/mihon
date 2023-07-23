package eu.kanade.presentation.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols().apply { decimalSeparator = '.' },
)

fun formatChapterNumber(chapterNumber: Float): String {
    return formatter.format(chapterNumber)
}
