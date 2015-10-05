package eu.kanade.mangafeed;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import eu.kanade.mangafeed.data.caches.CacheManager;
import eu.kanade.mangafeed.data.helpers.NetworkHelper;
import eu.kanade.mangafeed.sources.Batoto;
import rx.observers.TestSubscriber;

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
        TestSubscriber a = new TestSubscriber();

        b.pullImageUrlsFromNetwork(chapterUrl).subscribe(a);
        a.assertNoErrors();
    }
}
