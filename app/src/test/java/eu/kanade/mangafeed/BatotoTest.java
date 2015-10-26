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
import eu.kanade.mangafeed.sources.Batoto;
import eu.kanade.mangafeed.sources.base.Source;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class BatotoTest {

    NetworkHelper net;
    CacheManager cache;
    Source b;
    final String chapterUrl ="http://bato.to/read/_/345144/minamoto-kun-monogatari_ch178_by_vortex-scans";
    final String mangaUrl = "http://bato.to/comic/_/comics/natsuzora-and-run-r9597";
    final String mangaUrl2 = "http://bato.to/comic/_/comics/bungaku-shoujo-to-shinitagari-no-pierrot-r534";
    final String nisekoiUrl = "http://bato.to/comic/_/comics/nisekoi-r951";

    @Before
    public void setUp() {
        net = new NetworkHelper();
        cache = new CacheManager(RuntimeEnvironment.application.getApplicationContext());
        b = new Batoto(net, cache);
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
        Assert.assertNotNull(m.artist);
        Assert.assertNotNull(m.author);
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
        Manga nisekoi = b.pullMangaFromNetwork(nisekoiUrl)
                .toBlocking().single();

        Assert.assertEquals("Nisekoi", nisekoi.title);
        Assert.assertEquals("Komi Naoshi", nisekoi.author);
        Assert.assertEquals("Komi Naoshi", nisekoi.artist);
        Assert.assertEquals("http://bato.to/comic/_/nisekoi-r951", nisekoi.url);
        Assert.assertEquals("http://img.bato.to/forums/uploads/a2a850c644a50bccc462f36922c1cbf2.jpg", nisekoi.thumbnail_url);
        Assert.assertTrue(nisekoi.description.length() > 20);
        Assert.assertTrue(nisekoi.genre.length() > 20);
    }
}
