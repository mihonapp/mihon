package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition.parseChapterNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ChapterRecognitionTest {

    @Test
    fun `Basic Ch prefix`() {
        val manga = createManga("Mokushiroku Alice")

        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4f)
    }

    @Test
    fun `Basic Ch prefix with space after period`() {
        val manga = createManga("Mokushiroku Alice")

        assertChapter(manga, "Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation", 4f)
    }

    @Test
    fun `Basic Ch prefix with decimal`() {
        val manga = createManga("Mokushiroku Alice")

        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation", 4.1f)
        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation", 4.4f)
    }

    @Test
    fun `Basic Ch prefix with alpha postfix`() {
        val manga = createManga("Mokushiroku Alice")

        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation", 4.1f)
        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation", 4.2f)
        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation", 4.99f)
    }

    @Test
    fun `Name containing one number`() {
        val manga = createManga("Bleach")

        assertChapter(manga, "Bleach 567 Down With Snowwhite", 567f)
    }

    @Test
    fun `Name containing one number and decimal`() {
        val manga = createManga("Bleach")

        assertChapter(manga, "Bleach 567.1 Down With Snowwhite", 567.1f)
        assertChapter(manga, "Bleach 567.4 Down With Snowwhite", 567.4f)
    }

    @Test
    fun `Name containing one number and alpha`() {
        val manga = createManga("Bleach")

        assertChapter(manga, "Bleach 567.a Down With Snowwhite", 567.1f)
        assertChapter(manga, "Bleach 567.b Down With Snowwhite", 567.2f)
        assertChapter(manga, "Bleach 567.extra Down With Snowwhite", 567.99f)
    }

    @Test
    fun `Chapter containing manga title and number`() {
        val manga = createManga("Solanin")

        assertChapter(manga, "Solanin 028 Vol. 2", 28f)
    }

    @Test
    fun `Chapter containing manga title and number decimal`() {
        val manga = createManga("Solanin")

        assertChapter(manga, "Solanin 028.1 Vol. 2", 28.1f)
        assertChapter(manga, "Solanin 028.4 Vol. 2", 28.4f)
    }

    @Test
    fun `Chapter containing manga title and number alpha`() {
        val manga = createManga("Solanin")

        assertChapter(manga, "Solanin 028.a Vol. 2", 28.1f)
        assertChapter(manga, "Solanin 028.b Vol. 2", 28.2f)
        assertChapter(manga, "Solanin 028.extra Vol. 2", 28.99f)
    }

    @Test
    fun `Extreme case`() {
        val manga = createManga("Onepunch-Man")

        assertChapter(manga, "Onepunch-Man Punch Ver002 028", 28f)
    }

    @Test
    fun `Extreme case with decimal`() {
        val manga = createManga("Onepunch-Man")

        assertChapter(manga, "Onepunch-Man Punch Ver002 028.1", 28.1f)
        assertChapter(manga, "Onepunch-Man Punch Ver002 028.4", 28.4f)
    }

    @Test
    fun `Extreme case with alpha`() {
        val manga = createManga("Onepunch-Man")

        assertChapter(manga, "Onepunch-Man Punch Ver002 028.a", 28.1f)
        assertChapter(manga, "Onepunch-Man Punch Ver002 028.b", 28.2f)
        assertChapter(manga, "Onepunch-Man Punch Ver002 028.extra", 28.99f)
    }

    @Test
    fun `Chapter containing dot v2`() {
        val manga = createManga("random")

        assertChapter(manga, "Vol.1 Ch.5v.2: Alones", 5f)
    }

    @Test
    fun `Number in manga title`() {
        val manga = createManga("Ayame 14")

        assertChapter(manga, "Ayame 14 1 - The summer of 14", 1f)
    }

    @Test
    fun `Space between ch x`() {
        val manga = createManga("Mokushiroku Alice")

        assertChapter(manga, "Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation", 4f)
    }

    @Test
    fun `Chapter title with ch substring`() {
        val manga = createManga("Ayame 14")

        assertChapter(manga, "Vol.1 Ch.1: March 25 (First Day Cohabiting)", 1f)
    }

    @Test
    fun `Chapter containing multiple zeros`() {
        val manga = createManga("random")

        assertChapter(manga, "Vol.001 Ch.003: Kaguya Doesn't Know Much", 3f)
    }

    @Test
    fun `Chapter with version before number`() {
        val manga = createManga("Onepunch-Man")

        assertChapter(manga, "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]", 86f)
    }

    @Test
    fun `Version attached to chapter number`() {
        val manga = createManga("Ansatsu Kyoushitsu")

        assertChapter(manga, "Ansatsu Kyoushitsu 011v002: Assembly Time", 11f)
    }

    /**
     * Case where the chapter title contains the chapter
     * But wait it's not actual the chapter number.
     */
    @Test
    fun `Number after manga title with chapter in chapter title case`() {
        val manga = createManga("Tokyo ESP")

        assertChapter(manga, "Tokyo ESP 027: Part 002: Chapter 001", 027f)
    }

    @Test
    fun `Unparseable chapter`() {
        val manga = createManga("random")

        assertChapter(manga, "Foo", -1f)
    }

    @Test
    fun `Chapter with time in title`() {
        val manga = createManga("random")

        assertChapter(manga, "Fairy Tail 404: 00:00", 404f)
    }

    @Test
    fun `Chapter with alpha without dot`() {
        val manga = createManga("random")

        assertChapter(manga, "Asu No Yoichi 19a", 19.1f)
    }

    @Test
    fun `Chapter title containing extra and vol`() {
        val manga = createManga("Fairy Tail")

        assertChapter(manga, "Fairy Tail 404.extravol002", 404.99f)
        assertChapter(manga, "Fairy Tail 404 extravol002", 404.99f)
        assertChapter(manga, "Fairy Tail 404.evol002", 404.5f)
    }

    @Test
    fun `Chapter title containing omake (japanese extra) and vol`() {
        val manga = createManga("Fairy Tail")

        assertChapter(manga, "Fairy Tail 404.omakevol002", 404.98f)
        assertChapter(manga, "Fairy Tail 404 omakevol002", 404.98f)
        assertChapter(manga, "Fairy Tail 404.ovol002", 404.15f)
    }

    @Test
    fun `Chapter title containing special and vol`() {
        val manga = createManga("Fairy Tail")

        assertChapter(manga, "Fairy Tail 404.specialvol002", 404.97f)
        assertChapter(manga, "Fairy Tail 404 specialvol002", 404.97f)
        assertChapter(manga, "Fairy Tail 404.svol002", 404.19f)
    }

    @Test
    fun `Chapter title containing commas`() {
        val manga = createManga("One Piece")

        assertChapter(manga, "One Piece 300,a", 300.1f)
        assertChapter(manga, "One Piece Ch,123,extra", 123.99f)
        assertChapter(manga, "One Piece the sunny, goes swimming 024,005", 24.005f)
    }

    @Test
    fun `Chapter title containing hyphens`() {
        val manga = createManga("Solo Leveling")

        assertChapter(manga, "ch 122-a", 122.1f)
        assertChapter(manga, "Solo Leveling Ch.123-extra", 123.99f)
        assertChapter(manga, "Solo Leveling, 024-005", 24.005f)
        assertChapter(manga, "Ch.191-200 Read Online", 191.200f)
    }

    @Test
    fun `Chapters containing season`() {
        val manga = createManga("D.I.C.E")

        assertChapter(manga, "D.I.C.E[Season 001] Ep. 007", 7f)
    }

    @Test
    fun `Chapters in format sx - chapter xx`() {
        val manga = createManga("The Gamer")

        assertChapter(manga, "S3 - Chapter 20", 20f)
    }

    @Test
    fun `Chapters ending with s`() {
        val manga = createManga("One Outs")

        assertChapter(manga, "One Outs 001", 1f)
    }

    private fun assertChapter(manga: Manga, name: String, expected: Float) {
        val chapter = Chapter.create()
        chapter.name = name

        parseChapterNumber(chapter, manga)
        assertEquals(expected, chapter.chapter_number)
    }

    private fun createManga(title: String): Manga {
        val manga = Manga.create(0)
        manga.title = title
        return manga
    }
}
