package eu.kanade.tachiyomi.data.database

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChapterRecognitionTest {

    private lateinit var manga: Manga
    private lateinit var chapter: Chapter

    private fun createChapter(name: String): Chapter {
        chapter = Chapter.create()
        chapter.name = name
        return chapter
    }

    private fun createManga(title: String): Manga {
        manga.title = title
        return manga
    }

    @BeforeEach
    fun setup() {
        manga = Manga.create(0).apply { title = "random" }
        chapter = Chapter.create()
    }

    /**
     * Ch.xx base case
     */
    @Test
    fun `ChCaseBase`() {
        createManga("Mokushiroku Alice")

        createChapter("Mokushiroku Alice Vol.1 Ch.4: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4f, chapter.chapter_number)
    }

    /**
     * Ch. xx base case but space after period
     */
    @Test
    fun ChCaseBase2() {
        createManga("Mokushiroku Alice")

        createChapter("Mokushiroku Alice Vol. 1 Ch. 4: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4f, chapter.chapter_number)
    }

    /**
     * Ch.xx.x base case
     */
    @Test
    fun ChCaseDecimal() {
        createManga("Mokushiroku Alice")

        createChapter("Mokushiroku Alice Vol.1 Ch.4.1: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4.1f, chapter.chapter_number)

        createChapter("Mokushiroku Alice Vol.1 Ch.4.4: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4.4f, chapter.chapter_number)
    }

    /**
     * Ch.xx.a base case
     */
    @Test
    fun ChCaseAlpha() {
        createManga("Mokushiroku Alice")

        createChapter("Mokushiroku Alice Vol.1 Ch.4.a: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4.1f, chapter.chapter_number)

        createChapter("Mokushiroku Alice Vol.1 Ch.4.b: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4.2f, chapter.chapter_number)

        createChapter("Mokushiroku Alice Vol.1 Ch.4.extra: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4.99f, chapter.chapter_number)
    }

    /**
     * Name containing one number base case
     */
    @Test
    fun OneNumberCaseBase() {
        createManga("Bleach")

        createChapter("Bleach 567 Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567f, chapter.chapter_number)
    }

    /**
     * Name containing one number and decimal case
     */
    @Test
    fun OneNumberCaseDecimal() {
        createManga("Bleach")

        createChapter("Bleach 567.1 Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567.1f, chapter.chapter_number)

        createChapter("Bleach 567.4 Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567.4f, chapter.chapter_number)
    }

    /**
     * Name containing one number and alpha case
     */
    @Test
    fun OneNumberCaseAlpha() {
        createManga("Bleach")

        createChapter("Bleach 567.a Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567.1f, chapter.chapter_number)

        createChapter("Bleach 567.b Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567.2f, chapter.chapter_number)

        createChapter("Bleach 567.extra Down With Snowwhite")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(567.99f, chapter.chapter_number)
    }

    /**
     * Chapter containing manga title and number base case
     */
    @Test
    fun MangaTitleCaseBase() {
        createManga("Solanin")

        createChapter("Solanin 028 Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28f, chapter.chapter_number)
    }

    /**
     * Chapter containing manga title and number decimal case
     */
    @Test
    fun MangaTitleCaseDecimal() {
        createManga("Solanin")

        createChapter("Solanin 028.1 Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.1f, chapter.chapter_number)

        createChapter("Solanin 028.4 Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.4f, chapter.chapter_number)
    }

    /**
     * Chapter containing manga title and number alpha case
     */
    @Test
    fun MangaTitleCaseAlpha() {
        createManga("Solanin")

        createChapter("Solanin 028.a Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.1f, chapter.chapter_number)

        createChapter("Solanin 028.b Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.2f, chapter.chapter_number)

        createChapter("Solanin 028.extra Vol. 2")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.99f, chapter.chapter_number)
    }

    /**
     * Extreme base case
     */
    @Test
    fun ExtremeCaseBase() {
        createManga("Onepunch-Man")

        createChapter("Onepunch-Man Punch Ver002 028")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28f, chapter.chapter_number)
    }

    /**
     * Extreme base case decimal
     */
    @Test
    fun ExtremeCaseDecimal() {
        createManga("Onepunch-Man")

        createChapter("Onepunch-Man Punch Ver002 028.1")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.1f, chapter.chapter_number)

        createChapter("Onepunch-Man Punch Ver002 028.4")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.4f, chapter.chapter_number)
    }

    /**
     * Extreme base case alpha
     */
    @Test
    fun ExtremeCaseAlpha() {
        createManga("Onepunch-Man")

        createChapter("Onepunch-Man Punch Ver002 028.a")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.1f, chapter.chapter_number)

        createChapter("Onepunch-Man Punch Ver002 028.b")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.2f, chapter.chapter_number)

        createChapter("Onepunch-Man Punch Ver002 028.extra")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(28.99f, chapter.chapter_number)
    }

    /**
     * Chapter containing .v2
     */
    @Test
    fun dotV2Case() {
        createChapter("Vol.1 Ch.5v.2: Alones")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(5f, chapter.chapter_number)
    }

    /**
     * Check for case with number in manga title
     */
    @Test
    fun numberInMangaTitleCase() {
        createManga("Ayame 14")
        createChapter("Ayame 14 1 - The summer of 14")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(1f, chapter.chapter_number)
    }

    /**
     * Case with space between ch. x
     */
    @Test
    fun spaceAfterChapterCase() {
        createManga("Mokushiroku Alice")
        createChapter("Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(4f, chapter.chapter_number)
    }

    /**
     * Chapter containing mar(ch)
     */
    @Test
    fun marchInChapterCase() {
        createManga("Ayame 14")
        createChapter("Vol.1 Ch.1: March 25 (First Day Cohabiting)")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(1f, chapter.chapter_number)
    }

    /**
     * Chapter containing range
     */
    @Test
    fun rangeInChapterCase() {
        createChapter("Ch.191-200 Read Online")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(191f, chapter.chapter_number)
    }

    /**
     * Chapter containing multiple zeros
     */
    @Test
    fun multipleZerosCase() {
        createChapter("Vol.001 Ch.003: Kaguya Doesn't Know Much")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(3f, chapter.chapter_number)
    }

    /**
     * Chapter with version before number
     */
    @Test
    fun chapterBeforeNumberCase() {
        createManga("Onepunch-Man")
        createChapter("Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(86f, chapter.chapter_number)
    }

    /**
     * Case with version attached to chapter number
     */
    @Test
    fun vAttachedToChapterCase() {
        createManga("Ansatsu Kyoushitsu")
        createChapter("Ansatsu Kyoushitsu 011v002: Assembly Time")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(11f, chapter.chapter_number)
    }

    /**
     * Case where the chapter title contains the chapter
     * But wait it's not actual the chapter number.
     */
    @Test
    fun NumberAfterMangaTitleWithChapterInChapterTitleCase() {
        createChapter("Tokyo ESP 027: Part 002: Chapter 001")
        createManga("Tokyo ESP")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(027f, chapter.chapter_number)
    }

    /**
     * unParsable chapter
     */
    @Test
    fun unParsableCase() {
        createChapter("Foo")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(-1f, chapter.chapter_number)
    }

    /**
     * chapter with time in title
     */
    @Test
    fun timeChapterCase() {
        createChapter("Fairy Tail 404: 00:00")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404f, chapter.chapter_number)
    }

    /**
     * chapter with alpha without dot
     */
    @Test
    fun alphaWithoutDotCase() {
        createChapter("Asu No Yoichi 19a")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(19.1f, chapter.chapter_number)
    }

    /**
     * Chapter title containing extra and vol
     */
    @Test
    fun chapterContainingExtraCase() {
        createManga("Fairy Tail")

        createChapter("Fairy Tail 404.extravol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.99f, chapter.chapter_number)

        createChapter("Fairy Tail 404 extravol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.99f, chapter.chapter_number)

        createChapter("Fairy Tail 404.evol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.5f, chapter.chapter_number)
    }

    /**
     * Chapter title containing omake (japanese extra) and vol
     */
    @Test
    fun chapterContainingOmakeCase() {
        createManga("Fairy Tail")

        createChapter("Fairy Tail 404.omakevol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.98f, chapter.chapter_number)

        createChapter("Fairy Tail 404 omakevol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.98f, chapter.chapter_number)

        createChapter("Fairy Tail 404.ovol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.15f, chapter.chapter_number)
    }

    /**
     * Chapter title containing special and vol
     */
    @Test
    fun chapterContainingSpecialCase() {
        createManga("Fairy Tail")

        createChapter("Fairy Tail 404.specialvol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.97f, chapter.chapter_number)

        createChapter("Fairy Tail 404 specialvol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.97f, chapter.chapter_number)

        createChapter("Fairy Tail 404.svol002")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(404.19f, chapter.chapter_number)
    }

    /**
     * Chapter title containing comma's
     */
    @Test
    fun chapterContainingCommasCase() {
        createManga("One Piece")

        createChapter("One Piece 300,a")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(300.1f, chapter.chapter_number)

        createChapter("One Piece Ch,123,extra")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(123.99f, chapter.chapter_number)

        createChapter("One Piece the sunny, goes swimming 024,005")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(24.005f, chapter.chapter_number)
    }

    /**
     * Test for chapters containing season
     */
    @Test
    fun chapterContainingSeasonCase() {
        createManga("D.I.C.E")

        createChapter("D.I.C.E[Season 001] Ep. 007")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(7f, chapter.chapter_number)
    }

    /**
     * Test for chapters in format sx - chapter xx
     */
    @Test
    fun chapterContainingSeasonCase2() {
        createManga("The Gamer")

        createChapter("S3 - Chapter 20")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(20f, chapter.chapter_number)
    }

    /**
     * Test for chapters ending with s
     */
    @Test
    fun chaptersEndingWithS() {
        createManga("One Outs")

        createChapter("One Outs 001")
        ChapterRecognition.parseChapterNumber(chapter, manga)
        assertEquals(1f, chapter.chapter_number)
    }
}
