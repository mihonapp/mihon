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
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.sources.Batoto;
import rx.android.schedulers.AndroidSchedulers;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class BatotoTest {

    NetworkHelper net;
    CacheManager cache;
    Batoto b;
    final String chapterUrl ="http://bato.to/read/_/345144/minamoto-kun-monogatari_ch178_by_vortex-scans";

    @Before
    public void setUp() {
        net = new NetworkHelper();
        cache = new CacheManager(RuntimeEnvironment.application.getApplicationContext());
        b = new Batoto(net, cache);
    }

    @Test
    public void testImageList() {
        List<String> imageUrls = b.getImageUrlsFromNetwork(chapterUrl)
                .toList().toBlocking().single();

        Assert.assertTrue(imageUrls.size() > 5);
    }

    @Test
    public void testMangaList() {
        List<Manga> mangaList = b.pullPopularMangasFromNetwork(1)
                .toBlocking().first();

        Assert.assertTrue(mangaList.size() > 25);
    }
}
