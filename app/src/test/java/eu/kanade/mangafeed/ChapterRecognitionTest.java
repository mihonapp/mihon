package eu.kanade.mangafeed;

import org.junit.Before;
import org.junit.Test;

import eu.kanade.mangafeed.data.database.models.Chapter;
import eu.kanade.mangafeed.data.database.models.Manga;
import eu.kanade.mangafeed.util.ChapterRecognition;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ChapterRecognitionTest {

    Manga randomManga;

    private Chapter createChapter(String title) {
        Chapter chapter = Chapter.create();
        chapter.name = title;
        return chapter;
    }

    @Before
    public void setUp() {
        randomManga = new Manga();
        randomManga.title = "Something";
    }

    @Test
    public void testWithOneDigit() {
        Chapter c = createChapter("Ch.3: Self-proclaimed Genius");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(3f));
    }

    @Test
    public void testWithVolumeBefore() {
        Chapter c = createChapter("Vol.1 Ch.4: Misrepresentation");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(4f));
    }

    @Test
    public void testWithVolumeAndVersionNumber() {
        Chapter c = createChapter("Vol.1 Ch.3 (v2) Read Online");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(3f));
    }

    @Test
    public void testWithVolumeAndNumberInTitle() {
        Chapter c = createChapter("Vol.15 Ch.90: Here Blooms the Daylily, Part 4");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(90f));
    }

    @Test
    public void testWithVolumeAndSpecialChapter() {
        Chapter c = createChapter("Vol.10 Ch.42.5: Homecoming (Beginning)");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(42.5f));
    }

    @Test
    public void testWithJustANumber() {
        Chapter c = createChapter("Homecoming (Beginning) 42");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(42f));
    }

    @Test
    public void testWithJustASpecialChapter() {
        Chapter c = createChapter("Homecoming (Beginning) 42.5");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(42.5f));
    }

    @Test
    public void testWithNumberinMangaTitle() {
        Chapter c = createChapter("3x3 Eyes 96");
        Manga m = new Manga();
        m.title = "3x3 Eyes";
        ChapterRecognition.parseChapterNumber(c, m);
        assertThat(c.chapter_number, is(96f));
    }

    @Test
    public void testWithColonAtTheEnd() {
        Chapter c = createChapter("Chapter 5: 365 days");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(5f));
    }

    @Test
    public void testWithZeros() {
        Chapter c = createChapter("Vol.001 Ch.003: Kaguya Doesn't Know Much");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(3f));
    }

    @Test
    public void testRange() {
        Chapter c = createChapter("Ch.191-200 Read Online");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(191f));
    }

    @Test
    public void testWithKeywordChAtTheEndOfTheManga() {
        // It should be 567, not 67 (ch is a keyword to get the chapter number)
        Chapter c = createChapter("Bleach 567: Down With Snowwhite");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(567f));
    }

    @Test
    public void testWithVersionBefore() {
        // It should be 84, not 2084
        Chapter c = createChapter("Onepunch-Man Punch Ver002 084 : Creeping Darkness");
        ChapterRecognition.parseChapterNumber(c, randomManga);
        assertThat(c.chapter_number, is(84f));
    }

}
