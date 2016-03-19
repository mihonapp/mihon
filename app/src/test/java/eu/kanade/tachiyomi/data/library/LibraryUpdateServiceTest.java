package eu.kanade.tachiyomi.data.library;

import android.content.Context;
import android.os.Build;
import android.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.tachiyomi.BuildConfig;
import eu.kanade.tachiyomi.CustomRobolectricGradleTestRunner;
import eu.kanade.tachiyomi.data.database.models.Chapter;
import eu.kanade.tachiyomi.data.database.models.Manga;
import eu.kanade.tachiyomi.data.source.base.Source;
import rx.Observable;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
@RunWith(CustomRobolectricGradleTestRunner.class)
public class LibraryUpdateServiceTest {

    ShadowApplication app;
    Context context;
    LibraryUpdateService service;
    Source source;

    @Before
    public void setup() {
        app = ShadowApplication.getInstance();
        context = app.getApplicationContext();
        service = Robolectric.setupService(LibraryUpdateService.class);
        source = mock(Source.class);
        when(service.sourceManager.get(anyInt())).thenReturn(source);
    }

    @Test
    public void testLifecycle() {
        // Smoke test
        Robolectric.buildService(LibraryUpdateService.class)
                .attach()
                .create()
                .startCommand(0, 0)
                .destroy()
                .get();
    }

    @Test
    public void testUpdateManga() {
        Manga manga = Manga.create("manga1");
        List<Chapter> chapters = createChapters("/chapter1", "/chapter2");

        when(source.pullChaptersFromNetwork(manga.url)).thenReturn(Observable.just(chapters));
        when(service.db.insertOrRemoveChapters(manga, chapters, source))
                .thenReturn(Observable.just(Pair.create(2, 0)));

        service.updateManga(manga).subscribe();

        verify(service.db).insertOrRemoveChapters(manga, chapters, source);
    }

    @Test
    public void testContinuesUpdatingWhenAMangaFails() {
        Manga manga1 = Manga.create("manga1");
        Manga manga2 = Manga.create("manga2");
        Manga manga3 = Manga.create("manga3");

        List<Manga> favManga = createManga("manga1", "manga2", "manga3");

        List<Chapter> chapters = createChapters("/chapter1", "/chapter2");
        List<Chapter> chapters3 = createChapters("/achapter1", "/achapter2");

        when(service.db.getFavoriteMangas().executeAsBlocking()).thenReturn(favManga);

        // One of the updates will fail
        when(source.pullChaptersFromNetwork("manga1")).thenReturn(Observable.just(chapters));
        when(source.pullChaptersFromNetwork("manga2")).thenReturn(Observable.<List<Chapter>>error(new Exception()));
        when(source.pullChaptersFromNetwork("manga3")).thenReturn(Observable.just(chapters3));

        when(service.db.insertOrRemoveChapters(manga1, chapters, source)).thenReturn(Observable.just(Pair.create(2, 0)));
        when(service.db.insertOrRemoveChapters(manga3, chapters, source)).thenReturn(Observable.just(Pair.create(2, 0)));

        service.updateLibrary().subscribe();

        // There are 3 network attempts and 2 insertions (1 request failed)
        verify(source, times(3)).pullChaptersFromNetwork((String)any());
        verify(service.db, times(2)).insertOrRemoveChapters((Manga)any(), anyListOf(Chapter.class), (Source)any());
        verify(service.db, never()).insertOrRemoveChapters(eq(manga2), anyListOf(Chapter.class), (Source)any());
    }

    private List<Chapter> createChapters(String... urls) {
        List<Chapter> list = new ArrayList<>();
        for (String url : urls) {
            Chapter c = Chapter.create();
            c.url = url;
            list.add(c);
        }
        return list;
    }

    private List<Manga> createManga(String... urls) {
        List<Manga> list = new ArrayList<>();
        for (String url : urls) {
            Manga m = Manga.create(url);
            list.add(m);
        }
        return list;
    }
}
