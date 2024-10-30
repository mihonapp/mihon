package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ChapterRecognitionTest {

    @Test
    fun `Basic Ch prefix`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4.0)
    }

    @Test
    fun `Basic Ch prefix with space after period`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation", 4.0)
    }

    @Test
    fun `Basic Ch prefix with decimal`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation", 4.1)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation", 4.4)
    }

    @Test
    fun `Basic Ch prefix with alpha postfix`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation", 4.1)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation", 4.2)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation", 4.99)
    }

    @Test
    fun `Name containing one number`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567 Down With Snowwhite", 567.0)
    }

    @Test
    fun `Name containing one number and decimal`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567.1 Down With Snowwhite", 567.1)
        assertChapter(mangaTitle, "Bleach 567.4 Down With Snowwhite", 567.4)
    }

    @Test
    fun `Name containing one number and alpha`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567.a Down With Snowwhite", 567.1)
        assertChapter(mangaTitle, "Bleach 567.b Down With Snowwhite", 567.2)
        assertChapter(mangaTitle, "Bleach 567.extra Down With Snowwhite", 567.99)
    }

    @Test
    fun `Chapter containing manga title and number`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028 Vol. 2", 28.0)
    }

    @Test
    fun `Chapter containing manga title and number decimal`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028.1 Vol. 2", 28.1)
        assertChapter(mangaTitle, "Solanin 028.4 Vol. 2", 28.4)
    }

    @Test
    fun `Chapter containing manga title and number alpha`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028.a Vol. 2", 28.1)
        assertChapter(mangaTitle, "Solanin 028.b Vol. 2", 28.2)
        assertChapter(mangaTitle, "Solanin 028.extra Vol. 2", 28.99)
    }

    @Test
    fun `Extreme case`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028", 28.0)
    }

    @Test
    fun `Extreme case with decimal`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.1", 28.1)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.4", 28.4)
    }

    @Test
    fun `Extreme case with alpha`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.a", 28.1)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.b", 28.2)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.extra", 28.99)
    }

    @Test
    fun `Chapter containing dot v2`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Vol.1 Ch.5v.2: Alones", 5.0)
    }

    @Test
    fun `Number in manga title`() {
        val mangaTitle = "Ayame 14"

        assertChapter(mangaTitle, "Ayame 14 1 - The summer of 14", 1.0)
    }

    @Test
    fun `Space between ch x`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation", 4.0)
    }

    @Test
    fun `Chapter title with ch substring`() {
        val mangaTitle = "Ayame 14"

        assertChapter(mangaTitle, "Vol.1 Ch.1: March 25 (First Day Cohabiting)", 1.0)
    }

    @Test
    fun `Chapter containing multiple zeros`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Vol.001 Ch.003: Kaguya Doesn't Know Much", 3.0)
    }

    @Test
    fun `Chapter with version before number`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]", 86.0)
    }

    @Test
    fun `Version attached to chapter number`() {
        val mangaTitle = "Ansatsu Kyoushitsu"

        assertChapter(mangaTitle, "Ansatsu Kyoushitsu 011v002: Assembly Time", 11.0)
    }

    /**
     * Case where the chapter title contains the chapter
     * But wait it's not actual the chapter number.
     */
    @Test
    fun `Number after manga title with chapter in chapter title case`() {
        val mangaTitle = "Tokyo ESP"

        assertChapter(mangaTitle, "Tokyo ESP 027: Part 002: Chapter 001", 027.0)
    }

    /**
     * Case where the chapter title contains the unwanted tag
     * But follow by chapter number.
     */
    @Test
    fun `Number after unwanted tag`() {
        val mangaTitle = "One-punch Man"

        assertChapter(mangaTitle, "Mag Version 195.5", 195.5)
    }

    @Test
    fun `Unparseable chapter`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Foo", -1.0)
    }

    @Test
    fun `Chapter with time in title`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Fairy Tail 404: 00:00", 404.0)
    }

    @Test
    fun `Chapter with alpha without dot`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Asu No Yoichi 19a", 19.1)
    }

    @Test
    fun `Chapter title containing extra and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.extravol002", 404.99)
        assertChapter(mangaTitle, "Fairy Tail 404 extravol002", 404.99)
    }

    @Test
    fun `Chapter title containing omake (japanese extra) and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.omakevol002", 404.98)
        assertChapter(mangaTitle, "Fairy Tail 404 omakevol002", 404.98)
    }

    @Test
    fun `Chapter title containing special and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.specialvol002", 404.97)
        assertChapter(mangaTitle, "Fairy Tail 404 specialvol002", 404.97)
    }

    @Test
    fun `Chapter title containing commas`() {
        val mangaTitle = "One Piece"

        assertChapter(mangaTitle, "One Piece 300,a", 300.1)
        assertChapter(mangaTitle, "One Piece Ch,123,extra", 123.99)
        assertChapter(mangaTitle, "One Piece the sunny, goes swimming 024,005", 24.005)
    }

    @Test
    fun `Chapter title containing hyphens`() {
        val mangaTitle = "Solo Leveling"

        assertChapter(mangaTitle, "ch 122-a", 122.1)
        assertChapter(mangaTitle, "Solo Leveling Ch.123-extra", 123.99)
        assertChapter(mangaTitle, "Solo Leveling, 024-005", 24.005)
        assertChapter(mangaTitle, "Ch.191-200 Read Online", 191.200)
    }

    @Test
    fun `Chapters containing season`() {
        assertChapter("D.I.C.E", "D.I.C.E[Season 001] Ep. 007", 7.0)
    }

    @Test
    fun `Chapters in format sx - chapter xx`() {
        assertChapter("The Gamer", "S3 - Chapter 20", 20.0)
    }

    @Test
    fun `Chapters ending with s`() {
        assertChapter("One Outs", "One Outs 001", 1.0)
    }

    @Test
    fun `Chapters containing ordinals`() {
        val mangaTitle = "The Sister of the Woods with a Thousand Young"

        assertChapter(mangaTitle, "The 1st Night", 1.0)
        assertChapter(mangaTitle, "The 2nd Night", 2.0)
        assertChapter(mangaTitle, "The 3rd Night", 3.0)
        assertChapter(mangaTitle, "The 4th Night", 4.0)
    }

    private fun assertChapter(mangaTitle: String, name: String, expected: Double) {
        ChapterRecognition.parseChapterNumber(mangaTitle, name) shouldBe expected
    }
}
