package eu.kanade.mangafeed.util;

import java.util.ArrayList;
import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Manga;

/**
 * Created by len on 8/10/15.
 */
public class DummyDataUtil {

    public static List<Manga> createDummyManga() {
        ArrayList<Manga> mangas = new ArrayList<>();
        mangas.add(createDummyManga("One Piece"));
        mangas.add(createDummyManga("Berserk"));
        mangas.add(createDummyManga("Horimiya"));
        mangas.add(createDummyManga("Übel Blatt"));

        return mangas;
    }

    private static Manga createDummyManga(String title) {
        Manga m = new Manga();
        m.title = title;
        m.url="http://example.com";
        m.artist="Eiichiro Oda";
        m.author="Eiichiro Oda";
        m.description="...";
        m.genre="Action, Drama";
        m.status="Ongoing";
        m.thumbnail_url="http://example.com/pic.png";
        return m;
    }

    public static List<Chapter> createDummyChapters() {
        List<Chapter> chapters = new ArrayList<>();
        Chapter c;

        for (int i = 1; i < 50; i++) {
            c = new Chapter();
            c.manga_id = 1L;
            c.name = "Chapter " + i;
            c.url = "http://example.com/1";
            chapters.add(c);
        }

        return chapters;
    }

}
