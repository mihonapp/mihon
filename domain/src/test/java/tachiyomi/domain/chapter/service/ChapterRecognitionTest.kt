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

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4f)
    }

    @Test
    fun `Basic Ch prefix with space after period`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation", 4f)
    }

    @Test
    fun `Basic Ch prefix with decimal`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation", 4.1f)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation", 4.4f)
    }

    @Test
    fun `Basic Ch prefix with alpha postfix`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation", 4.1f)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation", 4.2f)
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation", 4.99f)
    }

    @Test
    fun `Name containing one number`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567 Down With Snowwhite", 567f)
    }

    @Test
    fun `Name containing one number and decimal`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567.1 Down With Snowwhite", 567.1f)
        assertChapter(mangaTitle, "Bleach 567.4 Down With Snowwhite", 567.4f)
    }

    @Test
    fun `Name containing one number and alpha`() {
        val mangaTitle = "Bleach"

        assertChapter(mangaTitle, "Bleach 567.a Down With Snowwhite", 567.1f)
        assertChapter(mangaTitle, "Bleach 567.b Down With Snowwhite", 567.2f)
        assertChapter(mangaTitle, "Bleach 567.extra Down With Snowwhite", 567.99f)
    }

    @Test
    fun `Chapter containing manga title and number`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028 Vol. 2", 28f)
    }

    @Test
    fun `Chapter containing manga title and number decimal`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028.1 Vol. 2", 28.1f)
        assertChapter(mangaTitle, "Solanin 028.4 Vol. 2", 28.4f)
    }

    @Test
    fun `Chapter containing manga title and number alpha`() {
        val mangaTitle = "Solanin"

        assertChapter(mangaTitle, "Solanin 028.a Vol. 2", 28.1f)
        assertChapter(mangaTitle, "Solanin 028.b Vol. 2", 28.2f)
        assertChapter(mangaTitle, "Solanin 028.extra Vol. 2", 28.99f)
    }

    @Test
    fun `Extreme case`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028", 28f)
    }

    @Test
    fun `Extreme case with decimal`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.1", 28.1f)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.4", 28.4f)
    }

    @Test
    fun `Extreme case with alpha`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.a", 28.1f)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.b", 28.2f)
        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 028.extra", 28.99f)
    }

    @Test
    fun `Chapter containing dot v2`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Vol.1 Ch.5v.2: Alones", 5f)
    }

    @Test
    fun `Number in manga title`() {
        val mangaTitle = "Ayame 14"

        assertChapter(mangaTitle, "Ayame 14 1 - The summer of 14", 1f)
    }

    @Test
    fun `Space between ch x`() {
        val mangaTitle = "Mokushiroku Alice"

        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation", 4f)
    }

    @Test
    fun `Chapter title with ch substring`() {
        val mangaTitle = "Ayame 14"

        assertChapter(mangaTitle, "Vol.1 Ch.1: March 25 (First Day Cohabiting)", 1f)
    }

    @Test
    fun `Chapter containing multiple zeros`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Vol.001 Ch.003: Kaguya Doesn't Know Much", 3f)
    }

    @Test
    fun `Chapter with version before number`() {
        val mangaTitle = "Onepunch-Man"

        assertChapter(mangaTitle, "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]", 86f)
    }

    @Test
    fun `Version attached to chapter number`() {
        val mangaTitle = "Ansatsu Kyoushitsu"

        assertChapter(mangaTitle, "Ansatsu Kyoushitsu 011v002: Assembly Time", 11f)
    }

    /**
     * Case where the chapter title contains the chapter
     * But wait it's not actual the chapter number.
     */
    @Test
    fun `Number after manga title with chapter in chapter title case`() {
        val mangaTitle = "Tokyo ESP"

        assertChapter(mangaTitle, "Tokyo ESP 027: Part 002: Chapter 001", 027f)
    }

    @Test
    fun `Unparseable chapter`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Foo", -1f)
    }

    @Test
    fun `Chapter with time in title`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Fairy Tail 404: 00:00", 404f)
    }

    @Test
    fun `Chapter with alpha without dot`() {
        val mangaTitle = "random"

        assertChapter(mangaTitle, "Asu No Yoichi 19a", 19.1f)
    }

    @Test
    fun `Chapter title containing extra and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.extravol002", 404.99f)
        assertChapter(mangaTitle, "Fairy Tail 404 extravol002", 404.99f)
    }

    @Test
    fun `Chapter title containing omake (japanese extra) and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.omakevol002", 404.98f)
        assertChapter(mangaTitle, "Fairy Tail 404 omakevol002", 404.98f)
    }

    @Test
    fun `Chapter title containing special and vol`() {
        val mangaTitle = "Fairy Tail"

        assertChapter(mangaTitle, "Fairy Tail 404.specialvol002", 404.97f)
        assertChapter(mangaTitle, "Fairy Tail 404 specialvol002", 404.97f)
    }

    @Test
    fun `Chapter title containing commas`() {
        val mangaTitle = "One Piece"

        assertChapter(mangaTitle, "One Piece 300,a", 300.1f)
        assertChapter(mangaTitle, "One Piece Ch,123,extra", 123.99f)
        assertChapter(mangaTitle, "One Piece the sunny, goes swimming 024,005", 24.005f)
    }

    @Test
    fun `Chapter title containing hyphens`() {
        val mangaTitle = "Solo Leveling"

        assertChapter(mangaTitle, "ch 122-a", 122.1f)
        assertChapter(mangaTitle, "Solo Leveling Ch.123-extra", 123.99f)
        assertChapter(mangaTitle, "Solo Leveling, 024-005", 24.005f)
        assertChapter(mangaTitle, "Ch.191-200 Read Online", 191.200f)
    }

    @Test
    fun `Chapters containing season`() {
        assertChapter("D.I.C.E", "D.I.C.E[Season 001] Ep. 007", 7f)
    }

    @Test
    fun `Chapters in format sx - chapter xx`() {
        assertChapter("The Gamer", "S3 - Chapter 20", 20f)
    }

    @Test
    fun `Chapters ending with s`() {
        assertChapter("One Outs", "One Outs 001", 1f)
    }

    @Test
    fun `Chapters containing ordinals`() {
        val mangaTitle = "The Sister of the Woods with a Thousand Young"

        assertChapter(mangaTitle, "The 1st Night", 1f)
        assertChapter(mangaTitle, "The 2nd Night", 2f)
        assertChapter(mangaTitle, "The 3rd Night", 3f)
        assertChapter(mangaTitle, "The 4th Night", 4f)
    }

    private fun assertChapter(mangaTitle: String, name: String, expected: Float) {
        ChapterRecognition.parseChapterNumber(mangaTitle, name) shouldBe expected
    }
}
