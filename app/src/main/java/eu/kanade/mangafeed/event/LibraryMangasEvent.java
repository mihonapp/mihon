package eu.kanade.mangafeed.event;

import java.util.List;
import java.util.Map;

import eu.kanade.mangafeed.data.database.models.Manga;

public class LibraryMangasEvent {

    private final Map<Integer, List<Manga>> mangas;

    public LibraryMangasEvent(Map<Integer, List<Manga>> mangas) {
        this.mangas = mangas;
    }

    public Map<Integer, List<Manga>> getMangas() {
        return mangas;
    }
}
