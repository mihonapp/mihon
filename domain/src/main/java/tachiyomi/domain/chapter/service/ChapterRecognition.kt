package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.floor
import kotlin.math.round

/**
 * Recognizes the volume and chapter number of chapters from their names.
 *
 * Each name is first read on its own: the volume or season is taken out, a part becomes the letter of a sub-chapter,
 * and the chapter number is the number after "ch." or else the first one left. Reading the whole chapter list then
 * fills in what single names don't tell, see [parseChapterNumbers].
 *
 * -R> = regex conversion.
 */
object ChapterRecognition {

    /**
     * A number with optional decimals and a trailing word or letter, e.g. 12, 12.5, 12a, 12.extra
     */
    private const val NUMBER_PATTERN = """([0-9]+)(\.[0-9]+)?(\.?[a-z]+)?"""

    /**
     * Example: Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 4
     */
    private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")

    /**
     * Example: Bleach 567: Down With Snowwhite -R> 567
     */
    private val number = Regex(NUMBER_PATTERN)

    /**
     * Example: Onepunch-Man Punch Ver002 028 -R> Onepunch-Man Punch 028
     */
    private val unwanted = Regex("""\b(?:ver|version)[^a-z]?[0-9]+""")

    /**
     * Example: One Piece 12 special -R> One Piece 12special
     */
    private val unwantedWhiteSpace = Regex("""\s(?=extra|special|omake)""")

    /**
     * A volume or season, the short forms only right before the number since "s" also ends words.
     * Example: Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 1, The Gamer S3 - Chapter 20 -R> 3
     */
    private val volume = Regex("""\b(?:(?:vol(?:ume)?|season)[^a-z0-9]? *|[vs]\.?)([0-9]+(?:\.[0-9]+)?)\b""")

    /**
     * Example: Chapter 1.5 Part II -R> b
     */
    private val part = Regex("""\bpart\.? *([0-9]+|i{1,3}|iv|vi{0,3}|ix|x)\b""")

    private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")

    /**
     * Example: Group 2 Chapter 386 -R> Group # Chapter #
     */
    private val anyNumber = Regex("""[0-9]+(?:\.[0-9]+)?""")

    /**
     * A number after this is the chapter number, even when every similar name has the same one.
     * Example: Oshi no Ko Chapter 5 Part 1 -R> 5a
     */
    private val chapterPrefix = Regex("""(?:\b(?:ch|chap|chapter|ep|episode)\.? *|#)$""")

    /**
     * Example: Bonus Chapter: Beach Episode
     */
    private val bonus = Regex("""\b(?:extra|bonus|omake|special|side ?story|afterword|interlude)""")

    /**
     * Example: Prologue: The Beginning -R> 0
     */
    private val prologue = Regex("""\b(?:prologue|one.?shot|pilot)\b""")

    /**
     * Formats numbers without trailing zeros and with a dot whatever the locale, e.g. 10.5, 3, 24.005
     */
    private val formatter = DecimalFormat("#.###", DecimalFormatSymbols().apply { decimalSeparator = '.' })

    /**
     * The volume and chapter number recognized for a chapter, e.g. "10.5" or "19a", `null` when there is none.
     */
    data class ChapterNumbering(val volume: String?, val chapter: String?)

    /**
     * A recognized number, with the letter of a sub-chapter like the a in 19a. Kept apart from the value so that
     * chapters can still be compared and numbered after each other.
     */
    private data class RecognizedNumber(val value: Double, val letter: Char? = null) {
        override fun toString(): String = formatter.format(value) + (letter?.toString() ?: "")
    }

    /**
     * Recognizes the chapter number of a single chapter, keeping [chapterNumber] when the source gave one.
     */
    fun parseChapterNumber(
        mangaTitle: String,
        chapterName: String,
        chapterNumber: Double? = null,
    ): Double {
        if (chapterNumber != null && chapterNumber.isKnown()) return chapterNumber
        return parseName(cleanName(mangaTitle, chapterName))?.let { toDouble(it.toString()) } ?: chapterNumber ?: -1.0
    }

    /**
     * Recognizes the numbers of [chapters], given from the most to the least recent, using the whole list for what a
     * name alone doesn't tell: numbers every similar name shares aren't the chapter number, and chapters without a
     * number are numbered after the chapters around them. Numbers the source gave are kept.
     */
    fun parseChapterNumbers(mangaTitle: String, chapters: List<Chapter>): List<ChapterNumbering> {
        val names = chapters.map { cleanName(mangaTitle, it.name) }
        // Taken out before reading the names, so a number like the 2 in "Group 2 Chapter 386" can't be picked
        val sharedNumbers = findSharedNumbers(names, chapters)
        val chapterNumbers = Array(chapters.size) { index ->
            chapters[index].chapterNumber.takeIf { it.isKnown() }?.let(::RecognizedNumber)
                ?: parseName(names[index].removeRanges(sharedNumbers[index]))
        }
        // Only from the name: newer chapters may not be in a volume yet, so neighbours don't tell
        val volumes = Array(chapters.size) { volume.find(names[it])?.groupValues?.get(1)?.toDouble() }

        fillChapterNumbers(names, chapterNumbers, volumes)
        return chapters.indices.map {
            ChapterNumbering(volumes[it]?.let(formatter::format), chapterNumbers[it]?.toString())
        }
    }

    /**
     * Returns [number], a number of a [ChapterNumbering], as a double. A letter only counts without decimals, from .1
     * for a up to .9 for i, e.g. 19a -R> 19.1 and 1.5a -R> 1.5.
     */
    fun toDouble(number: String): Double {
        val letter = number.last().takeIf { it.isLetter() }
        val value = number.removeSuffix(letter?.toString() ?: "").toDouble()
        val tenths = letter?.let { it - 'a' + 1 }
        return if (tenths != null && tenths < 10 && '.' !in number) value + tenths / 10.0 else value
    }

    /**
     * Whether the source gave a number: -1 and below mean it didn't, except -2 which some sources use on purpose.
     */
    private fun Double.isKnown(): Boolean = this == -2.0 || this > -1.0

    private fun cleanName(mangaTitle: String, chapterName: String): String {
        return chapterName.lowercase().replace(mangaTitle.lowercase(), "")
    }

    /**
     * Replaces [ranges] with spaces, from the last so the earlier ones stay where they are.
     */
    private fun String.removeRanges(ranges: List<IntRange>): String {
        return ranges.sortedByDescending { it.first }.fold(this) { name, range -> name.replaceRange(range, " ") }
    }

    /**
     * Finds the numbers that are the same in every name of a group of at least three names that only differ in their
     * numbers, e.g. the 2 in "Group 2 Chapter 385". Chapters with a number from the source are left out.
     */
    private fun findSharedNumbers(names: List<String>, chapters: List<Chapter>): List<List<IntRange>> {
        val matches = names.map { anyNumber.findAll(it).toList() }
        val sharedNumbers = MutableList(names.size) { emptyList<IntRange>() }
        names.indices
            .filterNot { chapters[it].chapterNumber.isKnown() }
            // Grouped by all but their numbers, so that the nth number of each name means the same thing
            .groupBy { names[it].replace(anyNumber, "#") }
            .values
            // Fewer names could share a number by chance
            .filter { it.size >= 3 }
            .forEach { group ->
                val first = matches[group.first()]
                val (sharedSlots, otherSlots) = first.indices.partition { slot ->
                    group.all { matches[it][slot].value == first[slot].value }
                }
                // Something has to be left to tell the chapters apart
                if (otherSlots.isEmpty()) return@forEach
                // Parts of a chapter share its number, e.g. the 5 of "Chapter 5 Part 1" and "Chapter 5 Part 2"
                val removableSlots = sharedSlots.filterNot { slot ->
                    chapterPrefix.containsMatchIn(names[group.first()].substring(0, first[slot].range.first))
                }
                group.forEach { index -> sharedNumbers[index] = removableSlots.map { matches[index][it].range } }
            }
        return sharedNumbers
    }

    /**
     * Numbers runs of chapters without a number after the chapters around them, only within a volume since chapter
     * numbers may start over in the next one:
     * - between two chapters, the missing whole numbers when they fit, e.g. 2, ?, 4 -R> 3, or else 10, ?, 11 -R> 10.5
     * - after the newest chapter, only bonus chapters, e.g. 100, Afterword -R> 100.5
     * - before the oldest chapter, only a prologue, which gets 0, and volume 0 when the chapter after it has a volume
     */
    private fun fillChapterNumbers(
        names: List<String>,
        chapterNumbers: Array<RecognizedNumber?>,
        volumes: Array<Double?>,
    ) {
        chapterNumbers.nullRuns().forEach { run ->
            // A -2 from the source says nothing about where a chapter is, so it can't be numbered after
            val newer = chapterNumbers.getOrNull(run.first - 1)?.takeIf { it.value >= 0 }
            val older = chapterNumbers.getOrNull(run.last + 1)?.takeIf { it.value >= 0 }
            val olderVolume = volumes.getOrNull(run.last + 1)
            val bonuses = run.count { bonus.containsMatchIn(names[it]) }
            when {
                newer != null && older != null -> {
                    val isSameVolume = volumes[run.first - 1] == olderVolume && volumes.isWithinVolume(run, olderVolume)
                    // Out of order chapters tell nothing about the ones between them
                    if (newer.value <= older.value || !isSameVolume) return@forEach
                    val isWhole = listOf(newer, older).all { it.letter == null && it.value % 1.0 == 0.0 }
                    // Bonus chapters aren't missing chapters even when they would fit, e.g. 2, Bonus, 4 -R> 2.5
                    if (bonuses == 0 && isWhole && newer.value - older.value - 1 == run.count().toDouble()) {
                        run.forEach { chapterNumbers[it] = RecognizedNumber(older.value + (run.last + 1 - it)) }
                    } else {
                        // Never past the next whole number, which may still be a chapter to come
                        chapterNumbers.fillBetween(run, older.value, minOf(newer.value, floor(older.value) + 1))
                    }
                }
                older != null -> {
                    // Newer chapters without a number may as well be announcements
                    if (bonuses < run.count() || !volumes.isWithinVolume(run, olderVolume)) return@forEach
                    chapterNumbers.fillBetween(run, older.value, floor(older.value) + 1)
                }
                // When the first chapter is already 0, that's the prologue
                newer != null && newer.value > 0 -> {
                    run.filter { prologue.containsMatchIn(names[it]) }.forEach {
                        chapterNumbers[it] = RecognizedNumber(0.0)
                        if (volumes[it] == null && volumes[run.first - 1] != null) volumes[it] = 0.0
                    }
                }
            }
        }
    }

    /**
     * Returns the ranges of consecutive chapters without a number.
     */
    private fun Array<RecognizedNumber?>.nullRuns(): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = 0
        while (start < size) {
            if (this[start] != null) {
                start++
                continue
            }
            val end = (start..<size).firstOrNull { this[it] != null } ?: size
            runs.add(start..<end)
            start = end
        }
        return runs
    }

    /**
     * Whether the chapters of [run] are in no volume or in [volume], the one of the chapters their numbers come from.
     */
    private fun Array<Double?>.isWithinVolume(run: IntRange, volume: Double?): Boolean {
        return run.all { this[it] == null || this[it] == volume }
    }

    /**
     * Numbers [run] between [lower] and [upper], the least recent getting the lowest: .5, .6 and so on after the whole
     * number, e.g. 10, ?, ?, 11 -R> 10.5, 10.6, or evenly spread when those don't fit.
     */
    private fun Array<RecognizedNumber?>.fillBetween(run: IntRange, lower: Double, upper: Double) {
        val tenths = (5..9).map { floor(lower) + it / 10.0 }.filter { it > lower && it < upper }
        if (tenths.size >= run.count()) {
            // Rounded since tenths can't be exact doubles, e.g. 10 + 0.6
            run.reversed().forEachIndexed { index, chapter ->
                this[chapter] = RecognizedNumber(round(tenths[index] * 10) / 10)
            }
            return
        }
        // Tenths run out after e.g. a 2.9 or past five chapters, and the chapters still have to fit below upper
        val step = (upper - lower) / (run.count() + 1)
        run.forEach { this[it] = RecognizedNumber(round((lower + step * (run.last + 1 - it)) * 1000) / 1000) }
    }

    /**
     * Returns the chapter number in [name], a chapter name with the manga title removed, or `null` if it has none. A
     * part after the chapter number becomes its letter, e.g. Chapter 1.5 Part I -R> 1.5a.
     */
    private fun parseName(name: String): RecognizedNumber? {
        // The volume can't also be the chapter number
        val withoutVolume = volume.replace(name, " ")
        val partMatch = part.find(withoutVolume)
        val partLetter = partMatch?.groupValues?.get(1)?.let(::parsePart)?.takeIf { it in 1..26 }?.let { 'a' + it - 1 }
        if (partMatch != null && partLetter != null) {
            parseNumber(withoutVolume.removeRange(partMatch.range))?.let { number ->
                // A letter written in the number is what the source numbers the chapter by
                return if (number.letter == null) number.copy(letter = partLetter) else number
            }
        }
        // Without another number, e.g. "Part 3", the part is the chapter number
        return parseNumber(withoutVolume)
    }

    private fun parsePart(part: String): Int = part.toIntOrNull() ?: (romanNumerals.indexOf(part) + 1)

    /**
     * Returns the number after "ch." or else the first one, leaving out version tags when there are several.
     */
    private fun parseNumber(name: String): RecognizedNumber? {
        val cleanName = name
            .trim()
            // Some sources write decimals and letters with these, e.g. 300,a and 122-a
            .replace(',', '.')
            .replace('-', '.')
            .replace(unwantedWhiteSpace, "")

        val numbers = number.findAll(cleanName).toList()
        if (numbers.size > 1) {
            val withoutTags = unwanted.replace(cleanName, "")
            (basic.find(withoutTags) ?: number.find(withoutTags))?.let { return it.toRecognizedNumber() }
        }
        return numbers.firstOrNull()?.toRecognizedNumber()
    }

    /**
     * Example: 12.5 -R> 12.5, 12.extra -R> 12.99, 12.a -R> 12a
     */
    private fun MatchResult.toRecognizedNumber(): RecognizedNumber {
        val (whole, decimal, alpha) = destructured
        val value = whole.toDouble() + (decimal.toDoubleOrNull() ?: 0.0)
        return when {
            decimal.isEmpty() && "extra" in alpha -> RecognizedNumber(value + 0.99)
            decimal.isEmpty() && "omake" in alpha -> RecognizedNumber(value + 0.98)
            decimal.isEmpty() && "special" in alpha -> RecognizedNumber(value + 0.97)
            // Only up to i, like the decimals they used to stand for, since others are rather words like 5v2
            else -> RecognizedNumber(value, alpha.trimStart('.').singleOrNull()?.takeIf { it in 'a'..'i' })
        }
    }
}
