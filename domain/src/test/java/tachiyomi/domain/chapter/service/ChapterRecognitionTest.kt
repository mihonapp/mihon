package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.ChapterRecognition.ChapterNumbering

@Execution(ExecutionMode.CONCURRENT)
class ChapterRecognitionTest {

    @Test
    fun `Basic Ch prefix`() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun `Basic Ch prefix with space after period`() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun `Basic Ch prefix with decimal`() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation" to ChapterNumbering("1", "4.1"),
        )
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation" to ChapterNumbering("1", "4.4"),
        )
    }

    @Test
    fun `Basic Ch prefix with alpha postfix`() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation" to ChapterNumbering("1", "4a"),
        )
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation" to ChapterNumbering("1", "4b"),
        )
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation" to ChapterNumbering("1", "4.99"),
        )
    }

    @Test
    fun `Name containing one number`() {
        assertNumbers("Bleach", "Bleach 567 Down With Snowwhite" to ChapterNumbering(null, "567"))
    }

    @Test
    fun `Name containing one number and decimal`() {
        assertNumbers("Bleach", "Bleach 567.1 Down With Snowwhite" to ChapterNumbering(null, "567.1"))
        assertNumbers("Bleach", "Bleach 567.4 Down With Snowwhite" to ChapterNumbering(null, "567.4"))
    }

    @Test
    fun `Name containing one number and alpha`() {
        assertNumbers("Bleach", "Bleach 567.a Down With Snowwhite" to ChapterNumbering(null, "567a"))
        assertNumbers("Bleach", "Bleach 567.b Down With Snowwhite" to ChapterNumbering(null, "567b"))
        assertNumbers("Bleach", "Bleach 567.extra Down With Snowwhite" to ChapterNumbering(null, "567.99"))
    }

    @Test
    fun `Chapter containing manga title and number`() {
        assertNumbers("Solanin", "Solanin 028 Vol. 2" to ChapterNumbering("2", "28"))
    }

    @Test
    fun `Chapter containing manga title and number decimal`() {
        assertNumbers("Solanin", "Solanin 028.1 Vol. 2" to ChapterNumbering("2", "28.1"))
        assertNumbers("Solanin", "Solanin 028.4 Vol. 2" to ChapterNumbering("2", "28.4"))
    }

    @Test
    fun `Chapter containing manga title and number alpha`() {
        assertNumbers("Solanin", "Solanin 028.a Vol. 2" to ChapterNumbering("2", "28a"))
        assertNumbers("Solanin", "Solanin 028.b Vol. 2" to ChapterNumbering("2", "28b"))
        assertNumbers("Solanin", "Solanin 028.extra Vol. 2" to ChapterNumbering("2", "28.99"))
    }

    @Test
    fun `Extreme case`() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028" to ChapterNumbering(null, "28"))
    }

    @Test
    fun `Extreme case with decimal`() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.1" to ChapterNumbering(null, "28.1"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.4" to ChapterNumbering(null, "28.4"))
    }

    @Test
    fun `Extreme case with alpha`() {
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.a" to ChapterNumbering(null, "28a"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.b" to ChapterNumbering(null, "28b"))
        assertNumbers("Onepunch-Man", "Onepunch-Man Punch Ver002 028.extra" to ChapterNumbering(null, "28.99"))
    }

    @Test
    fun `Chapter containing dot v2`() {
        assertNumbers("random", "Vol.1 Ch.5v.2: Alones" to ChapterNumbering("1", "5"))
    }

    @Test
    fun `Number in manga title`() {
        assertNumbers("Ayame 14", "Ayame 14 1 - The summer of 14" to ChapterNumbering(null, "1"))
    }

    @Test
    fun `Space between ch x`() {
        assertNumbers(
            "Mokushiroku Alice",
            "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation" to ChapterNumbering("1", "4"),
        )
    }

    @Test
    fun `Chapter title with ch substring`() {
        assertNumbers("Ayame 14", "Vol.1 Ch.1: March 25 (First Day Cohabiting)" to ChapterNumbering("1", "1"))
    }

    @Test
    fun `Chapter containing multiple zeros`() {
        assertNumbers("random", "Vol.001 Ch.003: Kaguya Doesn't Know Much" to ChapterNumbering("1", "3"))
    }

    @Test
    fun `Chapter with version before number`() {
        assertNumbers(
            "Onepunch-Man",
            "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]" to ChapterNumbering(null, "86"),
        )
    }

    @Test
    fun `Version attached to chapter number`() {
        assertNumbers("Ansatsu Kyoushitsu", "Ansatsu Kyoushitsu 011v002: Assembly Time" to ChapterNumbering(null, "11"))
    }

    /**
     * Case where the chapter title contains the chapter
     * But wait it's not actual the chapter number.
     */
    @Test
    fun `Number after manga title with chapter in chapter title case`() {
        assertNumbers("Tokyo ESP", "Tokyo ESP 027: Part 002: Chapter 001" to ChapterNumbering(null, "27b"))
    }

    /**
     * Case where the chapter title contains the unwanted tag
     * But follow by chapter number.
     */
    @Test
    fun `Number after unwanted tag`() {
        assertNumbers("One-punch Man", "Mag Version 195.5" to ChapterNumbering(null, "195.5"))
    }

    @Test
    fun `Unparseable chapter`() {
        assertNumbers("random", "Foo" to ChapterNumbering(null, null))
    }

    @Test
    fun `Chapter with time in title`() {
        assertNumbers("random", "Fairy Tail 404: 00:00" to ChapterNumbering(null, "404"))
    }

    @Test
    fun `Chapter with alpha without dot`() {
        assertNumbers("random", "Asu No Yoichi 19a" to ChapterNumbering(null, "19a"))
    }

    @Test
    fun `Chapter title containing extra and vol`() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.extravol002" to ChapterNumbering(null, "404.99"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 extravol002" to ChapterNumbering(null, "404.99"))
    }

    @Test
    fun `Chapter title containing omake (japanese extra) and vol`() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.omakevol002" to ChapterNumbering(null, "404.98"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 omakevol002" to ChapterNumbering(null, "404.98"))
    }

    @Test
    fun `Chapter title containing special and vol`() {
        assertNumbers("Fairy Tail", "Fairy Tail 404.specialvol002" to ChapterNumbering(null, "404.97"))
        assertNumbers("Fairy Tail", "Fairy Tail 404 specialvol002" to ChapterNumbering(null, "404.97"))
    }

    @Test
    fun `Chapter title containing commas`() {
        assertNumbers("One Piece", "One Piece 300,a" to ChapterNumbering(null, "300a"))
        assertNumbers("One Piece", "One Piece Ch,123,extra" to ChapterNumbering(null, "123.99"))
        assertNumbers("One Piece", "One Piece the sunny, goes swimming 024,005" to ChapterNumbering(null, "24.005"))
    }

    @Test
    fun `Chapter title containing hyphens`() {
        assertNumbers("Solo Leveling", "ch 122-a" to ChapterNumbering(null, "122a"))
        assertNumbers("Solo Leveling", "Solo Leveling Ch.123-extra" to ChapterNumbering(null, "123.99"))
        assertNumbers("Solo Leveling", "Solo Leveling, 024-005" to ChapterNumbering(null, "24.005"))
        assertNumbers("Solo Leveling", "Ch.191-200 Read Online" to ChapterNumbering(null, "191.2"))
    }

    @Test
    fun `Chapters containing season`() {
        assertNumbers("D.I.C.E", "D.I.C.E[Season 001] Ep. 007" to ChapterNumbering("1", "7"))
    }

    @Test
    fun `Chapters in format sx - chapter xx`() {
        assertNumbers("The Gamer", "S3 - Chapter 20" to ChapterNumbering("3", "20"))
    }

    @Test
    fun `Chapters ending with s`() {
        assertNumbers("One Outs", "One Outs 001" to ChapterNumbering(null, "1"))
    }

    @Test
    fun `Chapters containing ordinals`() {
        val mangaTitle = "The Sister of the Woods with a Thousand Young"

        assertNumbers(mangaTitle, "The 1st Night" to ChapterNumbering(null, "1"))
        assertNumbers(mangaTitle, "The 2nd Night" to ChapterNumbering(null, "2"))
        assertNumbers(mangaTitle, "The 3rd Night" to ChapterNumbering(null, "3"))
        assertNumbers(mangaTitle, "The 4th Night" to ChapterNumbering(null, "4"))
    }

    @Test
    fun `Parts are letters`() {
        assertNumbers("Chapter 1.5 Part I" to ChapterNumbering(null, "1.5a"))
        assertNumbers("Chapter 1.5 Part II" to ChapterNumbering(null, "1.5b"))
        assertNumbers("Chapter 12 Part 3" to ChapterNumbering(null, "12c"))
        assertNumbers("Vol.2 Chapter 7 - Part IV" to ChapterNumbering("2", "7d"))
        assertNumbers("Part 3" to ChapterNumbering(null, "3"))
    }

    @Test
    fun `Numbers are turned into doubles`() {
        ChapterRecognition.toDouble("12") shouldBe 12.0
        ChapterRecognition.toDouble("10.5") shouldBe 10.5
        ChapterRecognition.toDouble("19a") shouldBe 19.1
        ChapterRecognition.toDouble("19i") shouldBe 19.9
        ChapterRecognition.toDouble("1.5a") shouldBe 1.5
        ChapterRecognition.toDouble("-2") shouldBe -2.0
    }

    @Test
    fun `Volume is not taken as the chapter number`() {
        assertNumbers("Mokushiroku Alice", "Mokushiroku Alice Vol.3" to ChapterNumbering("3", null))
        assertNumbers("Mokushiroku Alice", "Vol.3 12" to ChapterNumbering("3", "12"))
    }

    @Test
    fun `Seasons are volumes`() {
        assertNumbers("Vol-3 Chapter 2" to ChapterNumbering("3", "2"))
        assertNumbers("Mob's 100" to ChapterNumbering(null, "100"))
    }

    @Test
    fun `Volumes are only taken from the name`() {
        assertNumbers(
            "Vol.2 Ch.5" to ChapterNumbering("2", "5"),
            "Ch.4" to ChapterNumbering(null, "4"),
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
        )
    }

    @Test
    fun `Numbers every similar name shares are ignored`() {
        assertNumbers(
            "Group 2 Chapter 387" to ChapterNumbering(null, "387"),
            "Group 2 Chapter 386" to ChapterNumbering(null, "386"),
            "Group 2 Chapter 385" to ChapterNumbering(null, "385"),
        )
    }

    @Test
    fun `Shared numbers after a chapter prefix are kept`() {
        assertNumbers(
            "Chapter 5 Part 3" to ChapterNumbering(null, "5c"),
            "Chapter 5 Part 2" to ChapterNumbering(null, "5b"),
            "Chapter 5 Part 1" to ChapterNumbering(null, "5a"),
        )
    }

    @Test
    fun `Chapters without a number fill the missing whole numbers`() {
        assertNumbers(
            "Chapter 4" to ChapterNumbering(null, "4"),
            "The Return" to ChapterNumbering(null, "3"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
        assertNumbers(
            "Chapter 5" to ChapterNumbering(null, "5"),
            "Rain" to ChapterNumbering(null, "4"),
            "Snow" to ChapterNumbering(null, "3"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
    }

    @Test
    fun `Chapters without a number go between the chapters around them`() {
        assertNumbers(
            "Chapter 11" to ChapterNumbering(null, "11"),
            "Bonus: Beach Episode" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 13" to ChapterNumbering(null, "13"),
            "Intermission" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 11" to ChapterNumbering(null, "11"),
            "Rain" to ChapterNumbering(null, "10.6"),
            "Snow" to ChapterNumbering(null, "10.5"),
            "Chapter 10" to ChapterNumbering(null, "10"),
        )
        assertNumbers(
            "Chapter 4" to ChapterNumbering(null, "4"),
            "Side Story" to ChapterNumbering(null, "2.5"),
            "Chapter 2" to ChapterNumbering(null, "2"),
        )
        assertNumbers(
            "Chapter 3" to ChapterNumbering(null, "3"),
            "Extra" to ChapterNumbering(null, "2.6"),
            "Chapter 2.5" to ChapterNumbering(null, "2.5"),
        )
        assertNumbers(
            "Chapter 3" to ChapterNumbering(null, "3"),
            "Extra" to ChapterNumbering(null, "2.95"),
            "Chapter 2.9" to ChapterNumbering(null, "2.9"),
        )
    }

    @Test
    fun `Chapters without a number at the ends`() {
        assertNumbers(
            "Afterword" to ChapterNumbering(null, "100.5"),
            "Chapter 100" to ChapterNumbering(null, "100"),
        )
        assertNumbers(
            "Hiatus Announcement" to ChapterNumbering(null, null),
            "Chapter 100" to ChapterNumbering(null, "100"),
        )
        assertNumbers(
            "Chapter 1" to ChapterNumbering(null, "1"),
            "Prologue" to ChapterNumbering(null, "0"),
        )
        assertNumbers(
            "Chapter 1" to ChapterNumbering(null, "1"),
            "Character Profiles" to ChapterNumbering(null, null),
        )
    }

    @Test
    fun `A prologue before chapters in volumes is in volume 0`() {
        assertNumbers(
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
            "Prologue" to ChapterNumbering("0", "0"),
        )
        assertNumbers(
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
            "Vol.2 Prologue" to ChapterNumbering("2", "0"),
        )
    }

    @Test
    fun `Chapters without a number are only numbered within a volume`() {
        assertNumbers(
            "Vol.1 Ch.3" to ChapterNumbering("1", "3"),
            "Rain" to ChapterNumbering(null, "2"),
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Rain" to ChapterNumbering(null, null),
            "Vol.1 Ch.1" to ChapterNumbering("1", "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Vol.2 Rain" to ChapterNumbering("2", null),
            "Ch.1" to ChapterNumbering(null, "1"),
        )
        assertNumbers(
            "Vol.2 Ch.3" to ChapterNumbering("2", "3"),
            "Vol.1 Rain" to ChapterNumbering("1", null),
            "Vol.2 Ch.1" to ChapterNumbering("2", "1"),
        )
        assertNumbers(
            "Extra" to ChapterNumbering(null, "10.5"),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
        assertNumbers(
            "Vol.2 Extra" to ChapterNumbering("2", "10.5"),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
        assertNumbers(
            "Vol.3 Extra" to ChapterNumbering("3", null),
            "Vol.2 Ch.10" to ChapterNumbering("2", "10"),
        )
    }

    @Test
    fun `Numbers from the source are kept`() {
        val chapters = listOf(
            Chapter.create().copy(name = "Vol.1 Chapter 3", chapterNumber = 7.0),
            Chapter.create().copy(name = "Chapter 2", chapterNumber = -1.0),
        )

        ChapterRecognition.parseChapterNumbers("", chapters) shouldBe
            listOf(ChapterNumbering("1", "7"), ChapterNumbering(null, "2"))
    }

    private fun assertNumbers(vararg expected: Pair<String, ChapterNumbering>) = assertNumbers("", *expected)

    /**
     * Asserts the numbers recognized for chapters with the given names, listed from the most to the least recent.
     */
    private fun assertNumbers(mangaTitle: String, vararg expected: Pair<String, ChapterNumbering>) {
        val chapters = expected.map { (name, _) -> Chapter.create().copy(name = name, chapterNumber = -1.0) }
        ChapterRecognition.parseChapterNumbers(mangaTitle, chapters) shouldBe expected.map { it.second }
    }
}
