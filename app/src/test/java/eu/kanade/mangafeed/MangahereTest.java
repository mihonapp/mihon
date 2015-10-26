package eu.kanade.mangafeed;

import android.os.Build;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.MangaHere;
import eu.kanade.mangafeed.sources.base.Source;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class MangahereTest {

    NetworkHelper net;
    CacheManager cache;
    Source b;
    final String chapterUrl ="http://www.mangahere.co/manga/kimi_ni_todoke/v15/c099/";
    final String mangaUrl = "http://www.mangahere.co/manga/kimi_ni_todoke/";

    @Before
    public void setUp() {
        net = new NetworkHelper();
        cache = new CacheManager(RuntimeEnvironment.application.getApplicationContext());
        b = new MangaHere(net, cache);
    }

    @Test
    public void testImageList() {
        List<String> imageUrls = b.getRemainingImageUrlsFromPageList(chapterUrl)
                .toList().toBlocking().single();

        Assert.assertTrue(imageUrls.size() > 5);
    }

    @Test
    public void testMangaList() {
        List<Manga> mangaList = b.pullPopularMangasFromNetwork(1)
                .toBlocking().first();

        Manga m = mangaList.get(0);
        Assert.assertNotNull(m.title);
        Assert.assertNotNull(m.url);

        Assert.assertTrue(mangaList.size() > 25);
    }

    @Test
    public void testChapterList() {
        List<Chapter> mangaList = b.pullChaptersFromNetwork(mangaUrl)
                .toBlocking().first();

        Assert.assertTrue(mangaList.size() > 5);
    }

    @Test
    public void testMangaDetails() {
        Manga manga = b.pullMangaFromNetwork(mangaUrl)
                .toBlocking().single();

        Assert.assertEquals("Shiina Karuho", manga.author);
        Assert.assertEquals("Shiina Karuho", manga.artist);
        Assert.assertEquals("http://www.mangahere.co/manga/kimi_ni_todoke/", manga.url);
        Assert.assertEquals("http://a.mhcdn.net/store/manga/4999/cover.jpg?v=1433950383", manga.thumbnail_url);
        Assert.assertTrue(manga.description.length() > 20);
        Assert.assertTrue(manga.genre.length() > 20);
    }
}
